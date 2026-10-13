package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.service.dto.GraphSeedConceptDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("Integration")
public class GraphSeedConceptServiceIntegrationTest {

    @Autowired
    private GraphSeedConceptService graphSeedConceptService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private ConceptSourceRepository conceptSourceRepository;

    private User user;
    private Notebook notebook;
    private Notebook otherNotebook;
    
    private Document docReady;
    private Document docFailed;
    private Document docOtherNotebook;
    
    private DocumentChunk chunkReady;
    private DocumentChunk chunkReadyStale;
    private DocumentChunk chunkFailed;
    private DocumentChunk chunkOtherNotebook;
    
    private Concept conceptA;
    private Concept conceptB;
    
    @BeforeEach
    void setup() {
        conceptSourceRepository.deleteAllInBatch();
        conceptRepository.deleteAllInBatch();
        documentChunkRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        notebookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        user = new User();
        user.setEmail("seed@example.com");
        user.setPassword("pass");
        user.setRole("STUDENT");
        user = userRepository.saveAndFlush(user);

        notebook = new Notebook();
        notebook.setTitle("Notebook A");
        notebook.setUser(user);
        notebook = notebookRepository.saveAndFlush(notebook);

        otherNotebook = new Notebook();
        otherNotebook.setTitle("Notebook B");
        otherNotebook.setUser(user);
        otherNotebook = notebookRepository.saveAndFlush(otherNotebook);

        // Docs
        docReady = new Document();
        docReady.setNotebook(notebook);
        docReady.setUser(user);
        docReady.setFileName("ready.pdf");
        docReady.setFilePath("/tmp");
        docReady.setStatus("READY");
        docReady.setActiveIndexVersion("v1");
        docReady = documentRepository.saveAndFlush(docReady);

        docFailed = new Document();
        docFailed.setNotebook(notebook);
        docFailed.setUser(user);
        docFailed.setFileName("failed.pdf");
        docFailed.setFilePath("/tmp");
        docFailed.setStatus("FAILED");
        docFailed.setActiveIndexVersion("v1");
        docFailed = documentRepository.saveAndFlush(docFailed);

        docOtherNotebook = new Document();
        docOtherNotebook.setNotebook(otherNotebook);
        docOtherNotebook.setUser(user);
        docOtherNotebook.setFileName("other.pdf");
        docOtherNotebook.setFilePath("/tmp");
        docOtherNotebook.setStatus("READY");
        docOtherNotebook.setActiveIndexVersion("v1");
        docOtherNotebook = documentRepository.saveAndFlush(docOtherNotebook);

        // Chunks
        chunkReady = new DocumentChunk();
        chunkReady.setDocument(docReady);
        chunkReady.setChunkIndex(0);
        chunkReady.setContent("Active");
        chunkReady.setIndexVersion("v1");
        chunkReady.setTokenCount(100);
        chunkReady = documentChunkRepository.saveAndFlush(chunkReady);

        chunkReadyStale = new DocumentChunk();
        chunkReadyStale.setDocument(docReady);
        chunkReadyStale.setChunkIndex(1);
        chunkReadyStale.setContent("Stale");
        chunkReadyStale.setIndexVersion("v0_stale");
        chunkReadyStale.setTokenCount(100);
        chunkReadyStale = documentChunkRepository.saveAndFlush(chunkReadyStale);

        chunkFailed = new DocumentChunk();
        chunkFailed.setDocument(docFailed);
        chunkFailed.setChunkIndex(0);
        chunkFailed.setContent("Failed");
        chunkFailed.setIndexVersion("v1");
        chunkFailed.setTokenCount(100);
        chunkFailed = documentChunkRepository.saveAndFlush(chunkFailed);

        chunkOtherNotebook = new DocumentChunk();
        chunkOtherNotebook.setDocument(docOtherNotebook);
        chunkOtherNotebook.setChunkIndex(0);
        chunkOtherNotebook.setContent("Other");
        chunkOtherNotebook.setIndexVersion("v1");
        chunkOtherNotebook.setTokenCount(100);
        chunkOtherNotebook = documentChunkRepository.saveAndFlush(chunkOtherNotebook);

        // Concepts
        conceptA = new Concept();
        conceptA.setNotebook(notebook);
        conceptA.setCanonicalName("A");
        conceptA.setNormalizedName("a");
        conceptA.setConceptType("T");
        conceptA = conceptRepository.saveAndFlush(conceptA);

        conceptB = new Concept();
        conceptB.setNotebook(notebook);
        conceptB.setCanonicalName("B");
        conceptB.setNormalizedName("b");
        conceptB.setConceptType("T");
        conceptB = conceptRepository.saveAndFlush(conceptB);
    }

    private void createSource(Concept c, DocumentChunk chunk) {
        ConceptSource src = new ConceptSource();
        src.setConcept(c);
        src.setDocumentChunk(chunk);
        src.setEvidenceText("Ev");
        conceptSourceRepository.saveAndFlush(src);
    }

    @Test
    void testP_activeReadySourceReturned() {
        createSource(conceptA, chunkReady);

        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(chunkReady.getId()).build());
        List<GraphSeedConceptDto> seeds = graphSeedConceptService.getSeedConcepts(notebook.getId(), chunks);

        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0).getConceptId()).isEqualTo(conceptA.getId());
    }

    @Test
    void testQ_staleSourceExcluded() {
        createSource(conceptA, chunkReadyStale);

        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(chunkReadyStale.getId()).build());
        List<GraphSeedConceptDto> seeds = graphSeedConceptService.getSeedConcepts(notebook.getId(), chunks);

        assertThat(seeds).isEmpty(); // R: Stale excluded
    }

    @Test
    void testS_failedDocumentSourceExcluded() {
        createSource(conceptA, chunkFailed);

        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(chunkFailed.getId()).build());
        List<GraphSeedConceptDto> seeds = graphSeedConceptService.getSeedConcepts(notebook.getId(), chunks);

        assertThat(seeds).isEmpty();
    }

    @Test
    void testU_crossNotebookConceptExcluded() {
        Concept conceptOther = new Concept();
        conceptOther.setNotebook(otherNotebook);
        conceptOther.setCanonicalName("Other");
        conceptOther.setNormalizedName("other");
        conceptOther.setConceptType("T");
        conceptOther = conceptRepository.saveAndFlush(conceptOther);

        createSource(conceptOther, chunkReady);

        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(chunkReady.getId()).build());
        List<GraphSeedConceptDto> seeds = graphSeedConceptService.getSeedConcepts(notebook.getId(), chunks);

        assertThat(seeds).isEmpty();
    }

    @Test
    void testV_crossNotebookSourceDocumentExcluded() {
        createSource(conceptA, chunkOtherNotebook); // A is in notebook, but chunk is in otherNotebook

        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(chunkOtherNotebook.getId()).build());
        List<GraphSeedConceptDto> seeds = graphSeedConceptService.getSeedConcepts(notebook.getId(), chunks);

        assertThat(seeds).isEmpty();
    }

    @Test
    void testW_mixedActiveAndStaleReturnsOnlyActive() {
        createSource(conceptA, chunkReady);
        createSource(conceptA, chunkReadyStale);

        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.builder().chunkId(chunkReady.getId()).build(),
                RetrievedChunk.builder().chunkId(chunkReadyStale.getId()).build()
        );
        List<GraphSeedConceptDto> seeds = graphSeedConceptService.getSeedConcepts(notebook.getId(), chunks);

        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0).getSupportingChunkCount()).isEqualTo(1); // Only active one counted
    }

    @Test
    void testR_staleOnlyConceptExcluded() {
        // Concept B only exists in stale chunk
        createSource(conceptB, chunkReadyStale);

        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(chunkReadyStale.getId()).build());
        List<GraphSeedConceptDto> seeds = graphSeedConceptService.getSeedConcepts(notebook.getId(), chunks);

        assertThat(seeds).isEmpty(); // R: Stale-only Concept excluded
    }

    @Test
    void testT_processingDocumentSourceExcluded() {
        Document docProcessing = new Document();
        docProcessing.setNotebook(notebook);
        docProcessing.setUser(user);
        docProcessing.setFileName("processing.pdf");
        docProcessing.setFilePath("/tmp");
        docProcessing.setStatus("PROCESSING");
        docProcessing.setActiveIndexVersion("v1");
        docProcessing = documentRepository.saveAndFlush(docProcessing);

        DocumentChunk chunkProcessing = new DocumentChunk();
        chunkProcessing.setDocument(docProcessing);
        chunkProcessing.setChunkIndex(0);
        chunkProcessing.setContent("Processing");
        chunkProcessing.setIndexVersion("v1");
        chunkProcessing.setTokenCount(100);
        chunkProcessing = documentChunkRepository.saveAndFlush(chunkProcessing);

        createSource(conceptA, chunkProcessing);

        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(chunkProcessing.getId()).build());
        List<GraphSeedConceptDto> seeds = graphSeedConceptService.getSeedConcepts(notebook.getId(), chunks);

        assertThat(seeds).isEmpty();
    }

    @Test
    void testX_multipleActiveChunksSameConcept() {
        DocumentChunk chunkReady2 = new DocumentChunk();
        chunkReady2.setDocument(docReady);
        chunkReady2.setChunkIndex(2);
        chunkReady2.setContent("Active 2");
        chunkReady2.setIndexVersion("v1"); // Same active index version
        chunkReady2.setTokenCount(100);
        chunkReady2 = documentChunkRepository.saveAndFlush(chunkReady2);

        createSource(conceptA, chunkReady);
        createSource(conceptA, chunkReady2);

        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.builder().chunkId(chunkReady.getId()).build(),
                RetrievedChunk.builder().chunkId(chunkReady2.getId()).build()
        );
        List<GraphSeedConceptDto> seeds = graphSeedConceptService.getSeedConcepts(notebook.getId(), chunks);

        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0).getConceptId()).isEqualTo(conceptA.getId());
        assertThat(seeds.get(0).getSupportingChunkCount()).isEqualTo(2);
    }

    @Test
    void testY_nullActiveIndexVersionExcluded() {
        Document docNullActive = new Document();
        docNullActive.setNotebook(notebook);
        docNullActive.setUser(user);
        docNullActive.setFileName("null.pdf");
        docNullActive.setFilePath("/tmp");
        docNullActive.setStatus("READY");
        docNullActive.setActiveIndexVersion(null); // NULL activeIndexVersion
        docNullActive = documentRepository.saveAndFlush(docNullActive);

        DocumentChunk chunkNullActive = new DocumentChunk();
        chunkNullActive.setDocument(docNullActive);
        chunkNullActive.setChunkIndex(0);
        chunkNullActive.setContent("Null Active");
        chunkNullActive.setIndexVersion("v1");
        chunkNullActive.setTokenCount(100);
        chunkNullActive = documentChunkRepository.saveAndFlush(chunkNullActive);

        createSource(conceptA, chunkNullActive);

        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().chunkId(chunkNullActive.getId()).build());
        List<GraphSeedConceptDto> seeds = graphSeedConceptService.getSeedConcepts(notebook.getId(), chunks);

        assertThat(seeds).isEmpty();
    }
}
