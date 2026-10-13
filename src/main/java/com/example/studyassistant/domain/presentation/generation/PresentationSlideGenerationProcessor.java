package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.*;
import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.retrieval.model.RetrievalResult;
import com.example.studyassistant.retrieval.model.RetrievedChunk;
import com.example.studyassistant.retrieval.service.RetrievalService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresentationSlideGenerationProcessor {

    private final PresentationGenerationJobRepository jobRepository;
    private final PresentationDeckRepository deckRepository;
    private final PresentationOutlineRevisionRepository outlineRepository;
    private final PresentationSlideRepository slideRepository;
    private final PresentationSlideElementRepository elementRepository;
    private final PresentationSlideSourceRepository sourceRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final TransactionTemplate transactionTemplate;
    private final LlmProvider llmProvider;
    private final RetrievalService retrievalService;
    private final PresentationJobFinalizerService finalizerService;
    private final Clock clock;
    private final SlideGenerationLifecycleHook lifecycleHook;
    @Qualifier("legacyJackson2ObjectMapper")
    private final ObjectMapper objectMapper;

    @Value("${presentation.slide.layout-mode:FIXED}")
    private String layoutMode;

    private boolean isFixedLayout() {
        return "FIXED".equalsIgnoreCase(layoutMode);
    }

    ParsedSlideResult parseFixedLayout(String output, List<RetrievedChunk> evidence, PlannedSlide plan) {
        if (output == null || output.isBlank()) {
            throw new SlideGenerationException.SlideSchemaViolationException("LLM response is empty");
        }
        Map<String, Object> root;
        try {
            root = objectMapper.readValue(SlideContentComposer.extractJson(output),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new SlideGenerationException.SlideSchemaViolationException("Invalid JSON format in LLM response");
        }
        return SlideContentComposer.compose(root, evidence == null ? 0 : evidence.size(),
                plan.layout(), plan.slideTitle());
    }


        record ResolvedSource(
            String alias,
            Long chunkId,
            Long documentId,
            String indexVersion,
            String evidenceExcerpt,
            Integer pageStart,
            Integer pageEnd,
            String sectionTitle) {}

        private record PreparedSlideResult(ParsedSlideResult parsedResult, List<ResolvedSource> sources) {}

    public void processJob(
            long jobId, long deckId, long ownerId,
            long notebookId, List<Long> frozenDocumentIds,
            long outlineRevisionId, String outlineContentHash,
            List<PlannedSlide> targetSlides) {

        log.info("Starting worker for job {}", jobId);

        boolean transitioned = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            PresentationGenerationJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
            if (job == null || job.getStatus() != PresentationJobStatus.QUEUED) return false;
            job.setStatus(PresentationJobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now(clock));
            return true;
        }));
        if (!transitioned) return;

        int failedCount = 0;

        for (PlannedSlide pSlide : targetSlides) {
            boolean shouldContinue = transactionTemplate.execute(status ->
                jobRepository.findByIdForUpdate(jobId)
                    .map(j -> j.getStatus() == PresentationJobStatus.RUNNING)
                    .orElse(false)
            );
            if (!shouldContinue) {
                log.info("Job {} aborted early", jobId);
                return;
            }

            try {
                try {
                    processSingleSlide(jobId, deckId, ownerId, notebookId, frozenDocumentIds, outlineRevisionId, outlineContentHash, pSlide);
                } catch (SlideGenerationException.SlideJobFailedFastException unsafeState) {
                    throw unsafeState;
                } catch (Exception firstFailure) {
                    log.warn("Retrying slide {} for job {}", pSlide.slideOrder(), jobId);
                    processSingleSlide(jobId, deckId, ownerId, notebookId, frozenDocumentIds, outlineRevisionId, outlineContentHash, pSlide);
                }
            } catch (SlideGenerationException.SlideJobFailedFastException e) {
                log.error("Job {} failed fast on slide {}: {}", jobId, pSlide.slideOrder(), e.getMessage());
                failCurrentPlaceholder(jobId, deckId, ownerId, outlineRevisionId, pSlide.slideOrder());
                finalizerService.finalizeSlideJob(jobId, ownerId, PresentationJobStatus.FAILED, e.getErrorCode(), e.getMessage());
                return;
            } catch (Exception e) {
                log.error("Failed to generate slide {} for job {}", pSlide.slideOrder(), jobId, e);
                failCurrentPlaceholder(jobId, deckId, ownerId, outlineRevisionId, pSlide.slideOrder());
                failedCount++;
            }
        }

        finalizerService.finalizeSlideJob(jobId, ownerId,
                failedCount == 0 ? PresentationJobStatus.SUCCEEDED : PresentationJobStatus.FAILED,
                failedCount == 0 ? null : "PARTIAL_SLIDE_FAILURE",
                failedCount == 0 ? null : failedCount + " slide chưa tạo được. Các slide thành công đã được giữ; hãy thử lại các slide lỗi.");
    }

    void processSingleSlide(
            long jobId, long deckId, long ownerId,
            long notebookId, List<Long> frozenDocumentIds,
            long outlineRevisionId, String outlineContentHash,
            PlannedSlide pSlide) throws Exception {

        PreparedSlideResult prepared = prepareSlide(ownerId, notebookId, frozenDocumentIds, pSlide);
        Long slideId = transactionTemplate.execute(status ->
            slideRepository.findByDeckIdAndSlideOrder(deckId, pSlide.slideOrder())
                .map(PresentationSlide::getId)
                .orElseThrow(() -> new IllegalStateException("Slide not found")));

        lifecycleHook.beforeSlidePersistence(jobId, slideId);

        transactionTemplate.executeWithoutResult(status -> persistPreparedSlide(
            jobId, deckId, ownerId, notebookId, frozenDocumentIds,
            outlineRevisionId, outlineContentHash, pSlide, slideId, prepared));
    }

    public static final Set<String> ALLOWED_TOP_LEVEL_KEYS = Set.of("title", "layout", "speakerNotes", "elements", "sourceRefs");
    public static final Set<String> ALLOWED_ELEMENT_KEYS = Set.of("type", "payload", "style", "x", "y", "width", "height");

    public String buildSystemPrompt() {
        return """
            You are a presentation slide generator. Generate the visual layout and text elements for a single slide. Canvas size is 960x540.

            OUTPUT RULES:
            - Output raw JSON ONLY.
            - Do NOT include markdown code fences (```json ... ```), markdown formatting, or any prose before or after the JSON.
            - Do NOT wrap the output object in any parent key like "content", "body", "slides", "data", "result", "slide", or "payload".
            - The JSON object MUST contain ONLY these 5 permitted top-level keys:
              1. "title" (string, non-empty)
              2. "layout" (string: "TITLE", "SECTION", "TITLE_CONTENT", or "SUMMARY")
              3. "speakerNotes" (string or null)
              4. "elements" (array of element objects)
              5. "sourceRefs" (array of string citation labels, e.g. ["S1", "S2"])
            - Forbidden top-level keys: "content", "body", "slides", "data", "result", "slide", "payload", "items", "components", "details". Including any forbidden or unknown top-level key will cause immediate rejection.

            ELEMENT RULES:
            - Each object in "elements" MUST contain ONLY these keys: "type", "payload", "style", "x", "y", "width", "height".
            - Permitted "type" values: "TEXT" or "SHAPE".
            - All coordinates and sizes must fit inside the 960x540 canvas bounds (x >= 0, y >= 0, x + width <= 960, y + height <= 540, width > 0, height > 0).
            - TEXT payload MUST contain ONLY: "text" (non-empty string).
            - TEXT style MAY contain ONLY: "fontSizePt" (number 8..144), "bold" (boolean), "italic" (boolean), "textAlign" ("LEFT", "CENTER", or "RIGHT"), "color" ("#RRGGBB" hex string).
            - SHAPE payload MUST contain ONLY: "primitive" ("RECTANGLE", "ROUNDED_RECTANGLE", "ELLIPSE", "LINE", or "ARROW") and optional "text".
            - SHAPE style MAY contain ONLY: "fillColor" ("#RRGGBB" hex string), "strokeColor" ("#RRGGBB" hex string), "strokeWidthPt" (number 0..20).

            CANONICAL JSON EXAMPLE:
            {
              "title": "Introduction to RAG",
              "layout": "TITLE_CONTENT",
              "speakerNotes": "Brief overview of RAG concepts",
              "elements": [
                {
                  "type": "TEXT",
                  "payload": { "text": "Introduction to RAG" },
                  "style": { "fontSizePt": 24, "bold": true, "color": "#1E293B" },
                  "x": 60,
                  "y": 40,
                  "width": 840,
                  "height": 60
                },
                {
                  "type": "TEXT",
                  "payload": { "text": "Retrieval Augmented Generation combines search with generative AI." },
                  "style": { "fontSizePt": 16, "color": "#334155" },
                  "x": 60,
                  "y": 120,
                  "width": 840,
                  "height": 360
                }
              ],
              "sourceRefs": ["S1"]
            }
            """;
    }

    public ParsedSlideResult parseAndValidateSlideJson(String llmOutput, List<RetrievedChunk> evidence, String plannedLayout) throws Exception {
        if (llmOutput == null || llmOutput.isBlank()) {
            throw new SlideGenerationException.SlideSchemaViolationException("LLM response is empty");
        }

        String cleaned = llmOutput.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1 && cleaned.endsWith("```")) {
                cleaned = cleaned.substring(firstNewline + 1, cleaned.length() - 3).trim();
            } else if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.substring(0, cleaned.length() - 3);
                }
                cleaned = cleaned.trim();
            }
        }

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            throw new SlideGenerationException.SlideSchemaViolationException("Invalid JSON format in LLM response: " + e.getMessage());
        }

        if (!rootNode.isObject()) {
            throw new SlideGenerationException.SlideSchemaViolationException("LLM response must be a JSON object");
        }

        rootNode.fieldNames().forEachRemaining(key -> {
            if (!ALLOWED_TOP_LEVEL_KEYS.contains(key)) {
                throw new SlideGenerationException.SlideSchemaViolationException("Unknown top-level field: " + key);
            }
        });

        JsonNode elementsNode = rootNode.get("elements");
        if (elementsNode != null && elementsNode.isArray()) {
            for (JsonNode elemNode : elementsNode) {
                if (!elemNode.isObject()) {
                    throw new SlideGenerationException.SlideSchemaViolationException("Element must be a JSON object");
                }
                elemNode.fieldNames().forEachRemaining(key -> {
                    if (!ALLOWED_ELEMENT_KEYS.contains(key)) {
                        throw new SlideGenerationException.SlideSchemaViolationException("Unknown element field: " + key);
                    }
                });
            }
        }

        JsonNode sourceRefsNode = rootNode.get("sourceRefs");
        if (sourceRefsNode != null && !sourceRefsNode.isNull()) {
            if (!sourceRefsNode.isArray()) {
                throw new SlideGenerationException.SlideSchemaViolationException("sourceRefs must be an array of strings");
            }
            for (JsonNode sourceRefNode : sourceRefsNode) {
                if (!sourceRefNode.isTextual()) {
                    throw new SlideGenerationException.SlideSchemaViolationException("sourceRef must be a string");
                }
            }
        }

        ParsedSlideResult parsedResult;
        try {
            parsedResult = objectMapper.treeToValue(rootNode, ParsedSlideResult.class);
        } catch (Exception e) {
            throw new SlideGenerationException.SlideSchemaViolationException("Failed to map JSON to ParsedSlideResult: " + e.getMessage());
        }

        Set<String> uniqueRefs = new LinkedHashSet<>();
        int evidenceCount = evidence != null ? evidence.size() : 0;
        for (String raw : parsedResult.sourceRefs() != null ? parsedResult.sourceRefs() : List.<String>of()) {
            if (raw == null) continue;
            String ref = raw.trim();
            if (ref.startsWith("[") && ref.endsWith("]")) ref = ref.substring(1, ref.length() - 1).trim();
            if (!ref.matches("S\\d+")) continue;
            try {
                int index = Integer.parseInt(ref.substring(1));
                if (index > 0 && index <= evidenceCount) uniqueRefs.add(ref);
            } catch (NumberFormatException ignored) { }
        }
        List<String> refs = new ArrayList<>(uniqueRefs);

        if (parsedResult.elements() == null) {
            throw new SlideGenerationException.SlideSchemaViolationException("Elements array cannot be null");
        }

        for (int i = 0; i < parsedResult.elements().size(); i++) {
            ParsedSlideResult.ParsedElement elem = parsedResult.elements().get(i);
            if (elem == null) {
                throw new SlideGenerationException.SlideSchemaViolationException("Element at index " + i + " is null");
            }
            SlideElementValidator.validateGeometry(elem.x(), elem.y(), elem.width(), elem.height(), i);
            if ("TEXT".equals(elem.type())) {
                SlideElementValidator.validateTextPayload(elem.payload(), i);
                SlideElementValidator.validateTextStyle(elem.style(), i);
            } else if ("SHAPE".equals(elem.type())) {
                SlideElementValidator.validateShapePayload(elem.payload(), i);
                SlideElementValidator.validateShapeStyle(elem.style(), i);
            } else {
                throw new SlideGenerationException.SlideSchemaViolationException("Unknown element type: " + elem.type());
            }
        }

        if (("CONTENT".equals(plannedLayout) || "TITLE_CONTENT".equals(plannedLayout) || "TWO_COLUMN".equals(plannedLayout) || "SUMMARY".equals(plannedLayout)) && refs.isEmpty()) {
            throw new SlideGenerationException.SlideSchemaViolationException("Content requires evidence");
        }

        return new ParsedSlideResult(parsedResult.title(), parsedResult.layout(), parsedResult.speakerNotes(), parsedResult.elements(), refs);
    }

    private PreparedSlideResult prepareSlide(
            long ownerId, long notebookId, List<Long> frozenDocumentIds, PlannedSlide pSlide) throws Exception {

        List<RetrievedChunk> evidence = approvedEvidence(ownerId, notebookId, frozenDocumentIds, pSlide);
        boolean fixedLayout = isFixedLayout();
        String systemPrompt = fixedLayout ? SlideContentComposer.systemPrompt() : buildSystemPrompt();

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Target Slide:\n");
        userPrompt.append("Title: ").append(pSlide.slideTitle()).append("\n");
        userPrompt.append("Layout: ").append(toPresentationLayout(pSlide.layout()).name()).append("\n");
        userPrompt.append("Purpose: ").append(pSlide.purpose()).append("\n");
        userPrompt.append("Language: ").append(pSlide.language()).append("\n");
        userPrompt.append("Content Points:\n");
        for (String pt : pSlide.contentPoints()) {
            userPrompt.append("- ").append(pt).append("\n");
        }
        userPrompt.append("\nEvidence:\n");
        for (int i = 0; i < evidence.size(); i++) {
            RetrievedChunk c = evidence.get(i);
            userPrompt.append("[Source S").append(i + 1).append("]: ").append(c.getContent()).append("\n");
        }

        String llmOutput = llmProvider.generate(systemPrompt, userPrompt.toString(), 0.2);

        ParsedSlideResult parsedResult = fixedLayout
                ? parseFixedLayout(llmOutput, evidence, pSlide)
                : parseAndValidateSlideJson(llmOutput, evidence, pSlide.layout());

        List<String> rawRefs = parsedResult.sourceRefs() != null ? parsedResult.sourceRefs() : List.of();
        Set<String> uniqueRefs = new LinkedHashSet<>(rawRefs);
        List<String> refs = new ArrayList<>(uniqueRefs);

        List<ResolvedSource> sources = resolveFreshSources(refs, evidence);
        return new PreparedSlideResult(parsedResult, List.copyOf(sources));
    }

    List<RetrievedChunk> approvedEvidence(long ownerId, long notebookId, List<Long> frozenIds, PlannedSlide plan) {
        if (plan.approvedSources() == null) {
            return retrievalService.search(ownerId, notebookId, plan.slideTitle() + " " + String.join(" ", plan.contentPoints()), frozenIds).getChunks();
        }
        List<RetrievedChunk> result = new ArrayList<>();
        for (PlannedSlide.ApprovedSource approved : plan.approvedSources()) {
            DocumentChunk chunk = documentChunkRepository.findById(approved.chunkId()).orElseThrow(() ->
                    new SlideGenerationException.SlideJobFailedFastException("STALE_SOURCE", "Approved chunk missing"));
            Document document = documentRepository.findById(approved.documentId()).orElseThrow(() ->
                    new SlideGenerationException.SlideJobFailedFastException("STALE_SOURCE", "Approved document missing"));
            ResolvedSource source = new ResolvedSource(approved.alias(), approved.chunkId(), approved.documentId(), approved.indexVersion(),
                    chunk.getContent(), chunk.getPageStart(), chunk.getPageEnd(), chunk.getSectionTitle());
            validateActiveCitation(source, chunk, document, ownerId, notebookId, frozenIds);
            result.add(RetrievedChunk.builder().chunkId(chunk.getId()).documentId(document.getId()).indexVersion(chunk.getIndexVersion())
                    .content(chunk.getContent()).pageStart(chunk.getPageStart()).pageEnd(chunk.getPageEnd()).sectionTitle(chunk.getSectionTitle()).build());
        }
        return List.copyOf(result);
    }

    static List<ResolvedSource> resolveFreshSources(List<String> refs, List<RetrievedChunk> evidence) {
        List<ResolvedSource> sources = new ArrayList<>();
        for (String ref : refs) {
            if (ref == null || !ref.matches("S\\d+")) {
                throw new SlideGenerationException.SlideSchemaViolationException("Invalid source ref: " + ref);
            }
            int index = Integer.parseInt(ref.substring(1)) - 1;
            if (index < 0 || evidence == null || index >= evidence.size()) {
                throw new SlideGenerationException.SlideSchemaViolationException("Source ref out of bounds: " + ref);
            }
            RetrievedChunk chunk = evidence.get(index);
            if (chunk == null || chunk.getChunkId() == null || chunk.getDocumentId() == null ||
                    chunk.getIndexVersion() == null || chunk.getIndexVersion().isBlank()) {
                throw new SlideGenerationException.SlideJobFailedFastException(
                        "STALE_SOURCE", "Retrieved citation is incomplete");
            }
            sources.add(new ResolvedSource(ref, chunk.getChunkId(), chunk.getDocumentId(), chunk.getIndexVersion(),
                    chunk.getContent(), chunk.getPageStart(), chunk.getPageEnd(), chunk.getSectionTitle()));
        }
        return List.copyOf(sources);
    }

    static void validateActiveCitation(
            ResolvedSource source,
            DocumentChunk chunk,
            Document document,
            long ownerId,
            long notebookId,
            List<Long> frozenDocumentIds) {
        if (source == null || chunk == null || document == null ||
                !Objects.equals(chunk.getId(), source.chunkId()) || chunk.getDocument() == null ||
                !Objects.equals(chunk.getDocument().getId(), source.documentId()) ||
                !Objects.equals(chunk.getIndexVersion(), source.indexVersion()) ||
                !Objects.equals(document.getId(), source.documentId()) ||
                !"READY".equals(document.getStatus()) ||
                document.getActiveIndexVersion() == null ||
                !Objects.equals(source.indexVersion(), document.getActiveIndexVersion()) ||
                document.getUser() == null || !Objects.equals(document.getUser().getId(), ownerId) ||
                document.getNotebook() == null || !Objects.equals(document.getNotebook().getId(), notebookId) ||
                frozenDocumentIds == null || !frozenDocumentIds.contains(document.getId())) {
            throw new SlideGenerationException.SlideJobFailedFastException(
                    "STALE_SOURCE", "Citation is no longer active in the frozen scope");
        }
    }

    private void persistPreparedSlide(
            long jobId, long deckId, long ownerId,
            long notebookId, List<Long> frozenDocumentIds,
            long outlineRevisionId, String outlineContentHash,
            PlannedSlide pSlide, Long slideId, PreparedSlideResult prepared) {

        PresentationDeck deck = deckRepository.findOwnedByIdForUpdate(deckId, ownerId)
                .orElseThrow(() -> new SlideGenerationException.SlideJobFailedFastException("DECK_NOT_FOUND", "Deck missing"));
        if (deck.getStatus() == PresentationDeckStatus.ARCHIVED) {
            throw new SlideGenerationException.SlideJobFailedFastException("DECK_ARCHIVED", "Deck archived");
        }

        PresentationOutlineRevision deckApprovedRevision = deck.getApprovedOutlineRevision();
        if (deckApprovedRevision == null || !Long.valueOf(outlineRevisionId).equals(deckApprovedRevision.getId())) {
            throw new SlideGenerationException.SlideJobFailedFastException("STALE_OUTLINE", "Outline modified");
        }
        PresentationOutlineRevision approvedRevision = outlineRepository
                .findByIdAndDeckIdForUpdate(deckApprovedRevision.getId(), deckId)
                .orElseThrow(() -> new SlideGenerationException.SlideJobFailedFastException("STALE_OUTLINE", "Outline modified"));
        if (approvedRevision.getStatus() != PresentationOutlineStatus.APPROVED ||
                !outlineContentHash.equals(approvedRevision.getContentHash())) {
            throw new SlideGenerationException.SlideJobFailedFastException("STALE_OUTLINE", "Outline modified");
        }

        PresentationGenerationJob job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found"));
        if (job.getStatus() != PresentationJobStatus.RUNNING) {
            throw new SlideGenerationException.SlideJobFailedFastException("JOB_STOPPED", "Job no longer running");
        }
        PresentationSlide slide = slideRepository.findByIdAndDeckId(slideId, deckId)
                .orElseThrow(() -> new IllegalStateException("Slide not found"));
        if (!slideOrderMatches(slide, pSlide) || !Long.valueOf(outlineRevisionId).equals(slide.getGeneratedFromOutlineRevisionId())) {
            throw new IllegalStateException("Revision or order mismatch on slide");
        }
        String expectedGenerationKey = job.getJobType() == PresentationJobType.SLIDE_RETRY
                ? job.getRequestHash()
                : "r" + outlineRevisionId + "-s" + pSlide.slideOrder();
        if (!expectedGenerationKey.equals(slide.getGenerationKey())) {
            throw new IllegalStateException("Key mismatch on slide");
        }
        if (slide.getGenerationState() == PresentationSlideGenerationState.GENERATED ||
                slide.getGenerationState() == PresentationSlideGenerationState.EDITED) {
            return;
        }
        if (slide.getGenerationState() != PresentationSlideGenerationState.EMPTY) {
            throw new IllegalStateException("Slide is not an EMPTY placeholder");
        }

        for (ResolvedSource source : prepared.sources()) {
            DocumentChunk chunk = documentChunkRepository.findById(source.chunkId())
                    .orElseThrow(() -> new SlideGenerationException.SlideJobFailedFastException("STALE_SOURCE", "Chunk missing"));
            Document document = documentRepository.findById(source.documentId())
                    .orElseThrow(() -> new SlideGenerationException.SlideJobFailedFastException("STALE_SOURCE", "Document missing"));
            validateActiveCitation(source, chunk, document, ownerId, notebookId, frozenDocumentIds);
        }

        elementRepository.deleteBySlideId(slide.getId());
        sourceRepository.deleteBySlideId(slide.getId());

        ParsedSlideResult parsedResult = prepared.parsedResult();
        slide.setTitle(parsedResult.title() != null ? parsedResult.title() : pSlide.slideTitle());
        slide.setLayoutType(parsedResult.layout() != null ? parsedResult.layout() : toPresentationLayout(pSlide.layout()));
        slide.setSpeakerNotes(parsedResult.speakerNotes());
        slide.setGenerationState(PresentationSlideGenerationState.GENERATED);
        slide.setUpdatedAt(LocalDateTime.now(clock));
        slideRepository.save(slide);

        int zIndex = 0;
        for (ParsedSlideResult.ParsedElement parsedElem : parsedResult.elements()) {
            PresentationSlideElement element = new PresentationSlideElement();
            element.setDeckId(deckId);
            element.setSlideId(slide.getId());
            element.setElementType(PresentationElementType.valueOf(parsedElem.type()));
            element.setZIndex(zIndex++);
            element.setX(parsedElem.x());
            element.setY(parsedElem.y());
            element.setWidth(parsedElem.width());
            element.setHeight(parsedElem.height());
            try {
                element.setPayloadJson(objectMapper.writeValueAsString(parsedElem.payload()));
                element.setStyleJson(objectMapper.writeValueAsString(parsedElem.style()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize element", e);
            }
            element.setCreatedAt(LocalDateTime.now(clock));
            element.setUpdatedAt(LocalDateTime.now(clock));
            elementRepository.save(element);
        }

        int sourceOrder = 1;
        for (ResolvedSource resolvedSource : prepared.sources()) {
            PresentationSlideSource source = new PresentationSlideSource();
            source.setSlide(slide);
            DocumentChunk chunkReference = new DocumentChunk();
            chunkReference.setId(resolvedSource.chunkId());
            source.setDocumentChunk(chunkReference);
            Document documentReference = new Document();
            documentReference.setId(resolvedSource.documentId());
            source.setDocument(documentReference);
            source.setEvidenceExcerpt(resolvedSource.evidenceExcerpt());
            source.setEvidenceHash(SlideContentHasher.evidenceHash(resolvedSource.evidenceExcerpt()));
            source.setSourceAlias(resolvedSource.alias());
            source.setPageStart(resolvedSource.pageStart());
            source.setPageEnd(resolvedSource.pageEnd());
            source.setSectionTitle(resolvedSource.sectionTitle());
            source.setSourceOrder(sourceOrder++);
            source.setCreatedAt(LocalDateTime.now(clock));
            sourceRepository.save(source);
        }

        job.setProgressCurrent(job.getProgressCurrent() + 1);
    }

    private boolean slideOrderMatches(PresentationSlide slide, PlannedSlide plannedSlide) {
        return slide.getSlideOrder() == plannedSlide.slideOrder();
    }

    private void failCurrentPlaceholder(long jobId, long deckId, long ownerId, long revisionId, int slideOrder) {
        transactionTemplate.executeWithoutResult(status -> {
            PresentationGenerationJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
            if (job == null || job.getStatus() != PresentationJobStatus.RUNNING ||
                    !job.getDeckId().equals(deckId) || !job.getOwner().getId().equals(ownerId) ||
                    !Long.valueOf(revisionId).equals(job.getOutlineRevisionId())) {
                return;
            }

            if (deckRepository.findOwnedByIdForUpdate(deckId, ownerId).isEmpty()) {
                return;
            }

            PresentationSlide slide = slideRepository.findByDeckIdAndSlideOrder(deckId, slideOrder).orElse(null);
            if (slide == null || !Long.valueOf(revisionId).equals(slide.getGeneratedFromOutlineRevisionId())) {
                return;
            }
            String expectedKey = job.getJobType() == PresentationJobType.SLIDE_RETRY
                    ? job.getRequestHash()
                    : "r" + revisionId + "-s" + slideOrder;
            if (!expectedKey.equals(slide.getGenerationKey()) ||
                    slide.getGenerationState() != PresentationSlideGenerationState.EMPTY) {
                return;
            }

            elementRepository.deleteBySlideId(slide.getId());
            sourceRepository.deleteBySlideId(slide.getId());
            slide.setGenerationState(PresentationSlideGenerationState.FAILED);
            slide.setUpdatedAt(LocalDateTime.now(clock));
            slideRepository.save(slide);
        });
    }

    private PresentationSlideLayout toPresentationLayout(String outlineSlideType) {
        return switch (outlineSlideType) {
            case "TITLE" -> PresentationSlideLayout.TITLE;
            case "SECTION" -> PresentationSlideLayout.SECTION;
            case "CONTENT" -> PresentationSlideLayout.TITLE_CONTENT;
            case "SUMMARY" -> PresentationSlideLayout.SUMMARY;
            default -> throw new IllegalArgumentException("Unsupported outline slide type: " + outlineSlideType);
        };
    }
}
