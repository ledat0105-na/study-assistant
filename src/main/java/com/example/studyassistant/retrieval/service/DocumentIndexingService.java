package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.retrieval.model.ChunkDraft;
import com.example.studyassistant.retrieval.model.ParsedDocument;
import com.example.studyassistant.retrieval.model.VectorRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentIndexingService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DocumentParsingService documentParsingService;

    @Autowired
    private ChunkingService chunkingService;

    @Autowired
    private EmbeddingProvider embeddingProvider;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private com.example.studyassistant.service.DocumentKnowledgeGraphGenerationService knowledgeGraphGenerationService;

    /**
     * Entry point for indexing. This orchestrates the flow without holding a DB transaction
     * during external network calls.
     */
    @Async("documentTaskExecutor")
    public void indexDocument(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        boolean isFirstIndex = (document.getActiveIndexVersion() == null);
        String oldVersion = document.getActiveIndexVersion();
        String oldModel = document.getEmbeddingModel();

        // If first index, optionally set processing state.
        if (isFirstIndex) {
            updateStatusInNewTransaction(documentId, "PROCESSING");
        }

        String candidateVersion = null;
        try {
            // 1. Compute Hash
            Path filePath = Paths.get(document.getFilePath());
            if (!Files.exists(filePath)) {
                throw new IllegalStateException("Original file not found for indexing: " + filePath);
            }
            String sourceHash = computeFileHash(filePath);

            // 2. Check ALREADY_INDEXED (No-Op)
            if (document.getContentHash() != null 
                && document.getContentHash().equals(sourceHash)
                && document.getActiveIndexVersion() != null
                && embeddingProvider.getModelIdentifier().equals(document.getEmbeddingModel())
                && "READY".equals(document.getStatus())) {
                log.info("Document {} already indexed with current model and unchanged content.", documentId);
                return; 
            }

            // 3. Prepare candidate
            candidateVersion = "v" + System.currentTimeMillis();
            
            // 4. Parse & Chunk
            ParsedDocument parsedDocument = documentParsingService.parse(filePath, document.getFileType());
            List<ChunkDraft> drafts = chunkingService.chunk(parsedDocument);
            
            if (drafts.isEmpty()) {
                throw new IllegalStateException("Document parsing produced no extractable content.");
            }

            // 5. Persist Candidate Chunks (Transaction 1 via Template)
            final String finalCandidate = candidateVersion;
            List<DocumentChunk> persistedChunks = transactionTemplate.execute(status -> {
                return saveCandidateChunks(document, drafts, finalCandidate);
            });

            // 6. Ensure Collection
            vectorStore.ensureCollection(embeddingProvider.getDimension());

            // 7. Embed & Upsert (Network bounds, NO DB TX here!)
            List<String> textsToEmbed = persistedChunks.stream()
                    .map(DocumentChunk::getContent)
                    .collect(Collectors.toList());
            
            List<List<Double>> embeddings = embeddingProvider.embedBatch(textsToEmbed);
            
            if (embeddings.size() != persistedChunks.size()) {
                throw new IllegalStateException("Embedding count mismatch.");
            }

            List<VectorRecord> records = new ArrayList<>(persistedChunks.size());
            for (int i = 0; i < persistedChunks.size(); i++) {
                DocumentChunk chunk = persistedChunks.get(i);
                List<Double> vector = embeddings.get(i);
                
                String pointId = generateDeterministicId(documentId, chunk.getChunkIndex(), finalCandidate);
                
                VectorRecord record = VectorRecord.builder()
                        .vectorId(pointId)
                        .vector(vector)
                        .chunkId(chunk.getId())
                        .documentId(documentId)
                        .indexVersion(finalCandidate)
                        .embeddingModel(embeddingProvider.getModelIdentifier())
                        .build();
                records.add(record);
            }

            vectorStore.upsert(records);

            // 8. Atomically Activate (Transaction 2 via Template)
            transactionTemplate.execute(status -> {
                activateIndex(documentId, finalCandidate, sourceHash, embeddingProvider.getModelIdentifier(), oldVersion);
                return null;
            });

            // Activation has committed. KG failure must not invalidate a healthy index.
            try {
                knowledgeGraphGenerationService.generate(document.getUser().getId(), documentId);
            } catch (Exception kgFailure) {
                log.warn("Knowledge graph generation unavailable for document {}; retry using mind-map/generate", documentId, kgFailure);
            }

            // 9. Best-effort Cleanup of old inactive version
            if (oldVersion != null && !oldVersion.equals(finalCandidate)) {
                cleanupHistoricalVersion(documentId, oldVersion);
            }

        } catch (Exception e) {
            log.error("Indexing failed for document {}", documentId, e);
            
            if (candidateVersion != null) {
                cleanupFailedCandidate(documentId, candidateVersion);
            }

            if (isFirstIndex) {
                transactionTemplate.execute(status -> {
                    updateStatusAndError(documentId, "FAILED", e.getMessage());
                    return null;
                });
            }
            // For re-index, old version remains active, no state change.
            throw new RuntimeException("Document indexing failed", e);
        }
    }

    public void updateStatusInNewTransaction(Long documentId, String status) {
        transactionTemplate.execute(tx -> {
            updateStatusAndError(documentId, status, null);
            return null;
        });
    }
    
    public void updateStatusAndError(Long documentId, String status, String errorMsg) {
        documentRepository.findByIdWithPessimisticLock(documentId).ifPresent(doc -> {
            if ("DELETED".equals(doc.getStatus())) return;
            doc.setStatus(status);
            doc.setErrorMessage(errorMsg);
            documentRepository.save(doc);
        });
    }

    public List<DocumentChunk> saveCandidateChunks(Document doc, List<ChunkDraft> drafts, String newVersion) {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (ChunkDraft draft : drafts) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(doc);
            chunk.setChunkIndex(draft.getChunkIndex());
            chunk.setContent(draft.getContent());
            chunk.setPageStart(draft.getPageStart());
            chunk.setPageEnd(draft.getPageEnd());
            chunk.setSectionTitle(draft.getSectionTitle());
            chunk.setTokenCount(draft.getTokenCount());
            chunk.setContentHash(draft.getContentHash());
            chunk.setIndexVersion(newVersion);
            chunk.setEmbeddingModel(embeddingProvider.getModelIdentifier());
            chunk.setCreatedAt(LocalDateTime.now());
            chunks.add(chunk);
        }
        return documentChunkRepository.saveAll(chunks);
    }

    public void activateIndex(Long documentId, String newVersion, String sourceHash, String modelIdentifier, String expectedOldVersion) {
        Document doc = documentRepository.findByIdWithPessimisticLock(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if ("DELETED".equals(doc.getStatus())) {
            throw new IllegalArgumentException("Document was deleted");
        }
        
        if (!java.util.Objects.equals(doc.getActiveIndexVersion(), expectedOldVersion)) {
            throw new IllegalStateException("Document was updated by another process");
        }
        
        doc.setActiveIndexVersion(newVersion);
        doc.setContentHash(sourceHash);
        doc.setEmbeddingModel(modelIdentifier);
        doc.setStatus("READY");
        doc.setErrorMessage(null);
        documentRepository.save(doc);
    }

    
    public void cleanupVersion(Long documentId, String version) {
        cleanupHistoricalVersion(documentId, version);
    }

    public void cleanupHistoricalVersion(Long documentId, String version) {
        try {
            // Delete from Vector Store first
            vectorStore.deleteByDocumentAndVersion(documentId, version);
        } catch (Exception e) {
            log.warn("Failed to cleanup historical vectors for doc {} version {}", documentId, version, e);
        }
        // NEVER delete old DB chunks for a historical version to preserve AI provenance
    }

    public void cleanupFailedCandidate(Long documentId, String candidateVersion) {
        try {
            vectorStore.deleteByDocumentAndVersion(documentId, candidateVersion);
        } catch (Exception e) {
            log.warn("Failed to cleanup vectors for candidate {} of doc {}", candidateVersion, documentId, e);
        }
        
        try {
            transactionTemplate.execute(status -> {
                Document doc = documentRepository.findById(documentId).orElse(null);
                // Ensure we don't accidentally delete chunks of the active version
                if (doc != null && !candidateVersion.equals(doc.getActiveIndexVersion())) {
                    documentChunkRepository.deleteByDocumentIdAndIndexVersion(documentId, candidateVersion);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to cleanup DB chunks for candidate {} of doc {}", candidateVersion, documentId, e);
        }
    }

    private String computeFileHash(Path filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(filePath);
        byte[] hash = digest.digest(bytes);
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String generateDeterministicId(Long documentId, int chunkIndex, String indexVersion) {
        String input = documentId + "_" + chunkIndex + "_" + indexVersion;
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8)).toString();
    }
}

