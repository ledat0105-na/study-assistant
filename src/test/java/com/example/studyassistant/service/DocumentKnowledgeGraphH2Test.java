package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@Import({ConceptService.class, ConceptPersistenceHelper.class, ConceptNameNormalizer.class,
    ConceptRelationService.class, ConceptRelationPersistenceHelper.class, KnowledgeGraphEvidenceValidator.class,
    AIConceptExtractor.class, AIConceptRelationExtractor.class, DocumentKnowledgeGraphGenerationService.class,
    KnowledgeGraphQueryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DocumentKnowledgeGraphH2Test {
    @Autowired UserRepository users;
    @Autowired NotebookRepository notebooks;
    @Autowired DocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;
    @Autowired ConceptRepository concepts;
    @Autowired ConceptSourceRepository sources;
    @Autowired ConceptRelationSourceRepository relationSources;
    @Autowired ConceptRelationRepository relations;
    @Autowired ConceptService conceptService;
    @Autowired ConceptRelationService relationService;
    @Autowired DocumentKnowledgeGraphGenerationService generation;
    @Autowired KnowledgeGraphQueryService queries;
    @Autowired AIConceptExtractor extractor;
    @Autowired AIConceptRelationExtractor relationExtractor;
    @Autowired KnowledgeGraphEvidenceValidator validator;
    @MockitoBean LlmProvider provider;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired javax.sql.DataSource dataSource;
    User owner;
    Document target;
    DocumentChunk active;

    @BeforeEach void fixture() {
        owner = user("STUDENT");
        target = document(owner, null);
        active = chunk(target, "v1");
        when(provider.generate(anyString(), anyString(), anyDouble())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (((String) inv.getArgument(0)).startsWith("Extract grounded relations")) {
                Concept java = lookup(target, "java");
                Concept jvm = lookup(target, "jvm");
                return "{\"relations\":[{\"sourceConceptId\":" + java.getId() + ",\"targetConceptId\":" + jvm.getId()
                        + ",\"relationType\":\"RELATED_TO\",\"confidence\":0.9,\"evidenceText\":\"Java runs on JVM\"}]}";
            }
            return "{\"concepts\":[{\"name\":\"Java\",\"description\":\"Language\",\"evidenceText\":\"Java runs on JVM\",\"confidence\":0.9},"
                    + "{\"name\":\"JVM\",\"evidenceText\":\"Java runs on JVM\",\"confidence\":0.9}]}";
        });
    }

    User user(String role) {
        User u = new User(); String unique = UUID.randomUUID().toString();
        u.setUsername(unique); u.setEmail(unique + "@kg.test"); u.setPassword("test"); u.setRole(role);
        return users.saveAndFlush(u);
    }
    Notebook notebook(User u) {
        Notebook n = new Notebook(); n.setUser(u); n.setTitle("KG test"); return notebooks.saveAndFlush(n);
    }
    Document document(User u, Notebook n) {
        Document d = new Document(); d.setUser(u); d.setNotebook(n); d.setFileName("KG");
        d.setFilePath("unused-test-only"); d.setFileType("PDF"); d.setStatus("READY");
        d.setActiveIndexVersion("v1"); d.setContentHash("hash-v1");
        d.setExtractedText("LEGACY TEXT MUST NOT REACH PROVIDER");
        return documents.saveAndFlush(d);
    }
    DocumentChunk chunk(Document d, String version) {
        DocumentChunk c = new DocumentChunk(); c.setDocument(d); c.setChunkIndex(0); c.setTokenCount(10);
        c.setContent("Java runs on JVM"); c.setIndexVersion(version); c.setPageStart(1); c.setPageEnd(1);
        return chunks.saveAndFlush(c);
    }
    Concept lookup(Document d, String name) {
        return (d.getNotebook() == null ? concepts.findByDocumentIdAndNormalizedName(d.getId(), name)
                : concepts.findByNotebookIdAndNormalizedName(d.getNotebook().getId(), name)).orElseThrow();
    }
    void generate() { generation.generate(owner.getId(), target.getId()); }

    @Test void regenerationRefreshesVietnameseDescriptionWithoutDuplicatingIdentityOrSource() {
        var first = conceptService.getOrCreateWithSource(KnowledgeScope.capture(target, owner.getId()),
                "Java", "A programming language", "TERM", active, "Java", 0.9);
        var refreshed = conceptService.getOrCreateWithSource(KnowledgeScope.capture(target, owner.getId()),
                "Java", "Ngôn ngữ lập trình được sử dụng trong tài liệu.", "TERM", active, "Java", 0.9);
        assertThat(refreshed.getId()).isEqualTo(first.getId());
        assertThat(refreshed.getConcept().getId()).isEqualTo(first.getConcept().getId());
        assertThat(concepts.findById(first.getConcept().getId()).orElseThrow().getDescription())
                .isEqualTo("Ngôn ngữ lập trình được sử dụng trong tài liệu.");
    }

    @Test void standaloneGeneratesGroundedConceptsAndRelations() {
        var graph = generation.generate(owner.getId(), target.getId());
        assertThat(graph.getScopeType()).isEqualTo("DOCUMENT");
        assertThat(graph.getGenerationStatus()).isEqualTo("FULL_SUCCESS");
        assertThat(graph.getNodes()).hasSize(2); assertThat(graph.getEdges()).hasSize(1);
        Concept java = lookup(target, "java");
        assertThat(java.getNotebook()).isNull(); assertThat(java.getDocument().getId()).isEqualTo(target.getId());
        var source = sources.findByConceptIdAndDocumentChunkId(java.getId(), active.getId()).orElseThrow();
        assertThat(source.getDocumentChunk().getId()).isEqualTo(active.getId());
        var relation = relationSources.findByConceptRelationIdAndDocumentChunkId(graph.getEdges().get(0).getId(), active.getId()).orElseThrow();
        assertThat(relation.getDocumentChunk().getId()).isEqualTo(active.getId());
        assertThat(relation.getEvidenceText()).isEqualTo("Java runs on JVM");
    }
    @Test void notebookNullExtractorEntryPointsDeriveDocumentScope() {
        assertThat(extractor.extractConcepts((Notebook) null, active).getAcceptedConcepts()).hasSize(2);
        assertThat(relationExtractor.extractRelationsForChunk((Notebook) null, active).getAcceptedRelations()).hasSize(1);
    }
    @Test void teacherNotebookScopePreservedAndStudentFlowShared() {
        owner.setRole("TEACHER"); users.saveAndFlush(owner);
        target = document(owner, notebook(owner)); active = chunk(target, "v1");
        var graph = generation.generate(owner.getId(), target.getId());
        assertThat(graph.getScopeType()).isEqualTo("NOTEBOOK");
        assertThat(lookup(target, "java").getDocument()).isNull();
        assertThat(lookup(target, "java").getNotebook().getId()).isEqualTo(target.getNotebook().getId());
    }
    @Test void bothScopesNullRejected() {
        Concept c = new Concept(); assertThatThrownBy(c::validateScope).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bothScopesPresentRejected() {
        Concept c = new Concept(); c.setNotebook(notebook(owner)); c.setDocument(target);
        assertThatThrownBy(c::validateScope).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void sameDocumentDeduplicatesConceptAndEvidenceOnRetry() {
        generate(); Long id = lookup(target, "java").getId();
        long conceptCount = concepts.count(), sourceCount = sources.count(), relationCount = relations.count(), relationSourceCount = relationSources.count();
        generate(); generate();
        assertThat(concepts.count()).isEqualTo(conceptCount);
        assertThat(sources.count()).isEqualTo(sourceCount);
        assertThat(relations.count()).isEqualTo(relationCount);
        assertThat(relationSources.count()).isEqualTo(relationSourceCount);
        assertThat(lookup(target, "java").getId()).isEqualTo(id);
        assertThat(queries.getGroundedKnowledgeGraphForDocument(target.getId(), owner.getId()).getNodes()).hasSize(2);
        assertThat(queries.getGroundedKnowledgeGraphForDocument(target.getId(), owner.getId()).getEdges()).hasSize(1);
    }
    @Test void sameNameInTwoStandaloneDocumentsDoesNotMergeOrLeak() {
        generate(); Long first = lookup(target, "java").getId(); Document original = target;
        target = document(owner, null); active = chunk(target, "v1"); generate();
        assertThat(lookup(target, "java").getId()).isNotEqualTo(first);
        assertThat(queries.getGroundedKnowledgeGraphForDocument(original.getId(), owner.getId()).getNodes())
                .extracting(n -> n.getId()).contains(first).doesNotContain(lookup(target, "java").getId());
    }
    @Test void documentUniqueConstraintRejectsDuplicateIdentity() {
        generate(); Concept duplicate = new Concept(); duplicate.setDocument(target);
        duplicate.setCanonicalName("Java"); duplicate.setNormalizedName("java");
        assertThatThrownBy(() -> concepts.saveAndFlush(duplicate)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void notebookDocumentViewExcludesUnrelatedSameNotebookEvidence() {
        Notebook n = notebook(owner); target = document(owner, n); active = chunk(target, "v1"); generate();
        Concept java = lookup(target, "java");
        Document unrelated = document(owner, n); DocumentChunk other = chunk(unrelated, "v1");
        conceptService.getOrCreateWithSource(KnowledgeScope.capture(unrelated, owner.getId()), "Other", "other", "TERM", other, "Java", .9);
        assertThat(queries.getGroundedKnowledgeGraphForDocument(target.getId(), owner.getId()).getNodes())
                .extracting(v -> v.getId()).contains(java.getId()).hasSize(2);
    }
    @Test void crossDocumentRelationRejected() {
        generate(); Concept first = lookup(target, "java");
        Document other = document(owner, null); DocumentChunk otherChunk = chunk(other, "v1");
        Concept second = conceptService.getOrCreateWithSource(KnowledgeScope.capture(other, owner.getId()), "Java", "", "TERM", otherChunk, "Java", .9).getConcept();
        assertThatThrownBy(() -> relationService.getOrCreateWithSource(first, second, RelationType.RELATED_TO, .9, active, "Java", .9))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void mixedNotebookDocumentRelationRejected() {
        generate(); Concept first = lookup(target, "java");
        Concept second = new Concept(); second.setId(-1L); second.setNotebook(notebook(owner));
        assertThatThrownBy(() -> KnowledgeScope.validateRelation(first, second)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void differentNotebookRelationRejected() {
        Concept a = new Concept(); a.setId(1L); a.setNotebook(notebook(owner));
        Concept b = new Concept(); b.setId(2L); b.setNotebook(notebook(owner));
        assertThatThrownBy(() -> KnowledgeScope.validateRelation(a, b)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void staleChunksRejectedBeforeProviderOrPersistence() {
        DocumentChunk stale = chunk(target, "old"); KnowledgeScope scope = KnowledgeScope.capture(target, owner.getId());
        assertThatThrownBy(() -> extractor.extractConcepts(scope, stale)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(provider);
    }
    @Test void staleAndForeignChunkCannotBePersistedAsConceptSource() {
        KnowledgeScope scope = KnowledgeScope.capture(target, owner.getId());
        DocumentChunk stale = chunk(target, "old");
        assertThatThrownBy(() -> conceptService.getOrCreateWithSource(scope, "Java", "", "TERM", stale, "Java", .9))
                .isInstanceOf(IllegalStateException.class);
        Document foreign = document(user("STUDENT"), null);
        DocumentChunk wrong = chunk(foreign, "v1");
        assertThatThrownBy(() -> conceptService.getOrCreateWithSource(scope, "Java", "", "TERM", wrong, "Java", .9))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(concepts.findByDocumentIdAndNormalizedName(target.getId(), "java")).isEmpty();
    }
    @Test void documentDeleteIsRestrictedWithoutErasingGroundedData() {
        generate();
        assertThatThrownBy(() -> documents.deleteById(target.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(documents.findById(target.getId())).isPresent();
        assertThat(queries.getGroundedKnowledgeGraphForDocument(target.getId(), owner.getId()).getNodes()).hasSize(2);
    }

    private DocumentService deletionService(com.example.studyassistant.retrieval.service.DocumentIndexingService indexer) {
        DocumentService service = new DocumentService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "documentRepository", documents);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "documentIndexingService", indexer);
        return service;
    }

    @Test void softDeleteReleasesQuotaAndPreservesGroundedHistory() {
        generate();
        Long conceptId = lookup(target, "java").getId();
        Long sourceId = sources.findByConceptIdAndDocumentChunkId(conceptId, active.getId()).orElseThrow().getId();
        Document remaining = document(owner, null);
        var indexer = mock(com.example.studyassistant.retrieval.service.DocumentIndexingService.class);
        var service = deletionService(indexer);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(tx -> {
            service.deleteDocument(owner.getId(), target.getId());
            verifyNoInteractions(indexer);
            return null;
        });
        verify(indexer).cleanupVersion(target.getId(), "v1");
        assertThat(documents.findById(target.getId())).isEmpty();
        assertThat(documents.findByIdAndUserId(target.getId(), owner.getId())).isEmpty();
        assertThat(documents.findKnowledgeGraphDocumentByIdAndUserId(target.getId(), owner.getId())).isEmpty();
        assertThat(documents.countByUserId(owner.getId())).isEqualTo(1);
        assertThat(documents.findByUserId(owner.getId())).extracting(Document::getId).containsExactly(remaining.getId());
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("select status from documents where id = ?", String.class, target.getId())).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select count(*) from concepts where id = ?", Long.class, conceptId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from concept_sources where id = ?", Long.class, sourceId)).isEqualTo(1);
        assertThatThrownBy(() -> queries.getGroundedKnowledgeGraphForDocument(target.getId(), owner.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(this::generate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rolledBackDeletionKeepsDocumentFileAndIndex() throws Exception {
        var file = java.nio.file.Files.createTempFile("delete-rollback-", ".pdf");
        try {
            target.setFilePath(file.toString()); documents.saveAndFlush(target);
            var indexer = mock(com.example.studyassistant.retrieval.service.DocumentIndexingService.class);
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(tx -> {
                deletionService(indexer).deleteDocument(owner.getId(), target.getId());
                tx.setRollbackOnly();
                return null;
            });
            assertThat(documents.findById(target.getId()).orElseThrow().getStatus()).isEqualTo("READY");
            assertThat(java.nio.file.Files.exists(file)).isTrue();
            verifyNoInteractions(indexer);
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }
    @Test void foreignDocumentDeniedForReadAndGeneration() {
        Long stranger = user("STUDENT").getId();
        assertThatThrownBy(() -> generation.generate(stranger, target.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queries.getGroundedKnowledgeGraphForDocument(target.getId(), stranger)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider);
    }
    @ParameterizedTest @ValueSource(strings = {"PROCESSING", "FAILED", "UPLOADING"})
    void nonReadyCannotGenerate(String status) {
        target.setStatus(status); documents.saveAndFlush(target);
        assertThatThrownBy(this::generate).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(provider);
    }
    @Test void nullActiveIndexCannotGenerate() {
        target.setActiveIndexVersion(null); documents.saveAndFlush(target);
        assertThatThrownBy(this::generate).isInstanceOf(IllegalStateException.class); verifyNoInteractions(provider);
    }
    @Test void reindexHidesOldProvenanceAndReusesIdentityWithFreshEvidence() {
        generate(); Long id = lookup(target, "java").getId();
        target.setActiveIndexVersion("v2"); target.setContentHash("hash-v2"); documents.saveAndFlush(target);
        assertThat(queries.getGroundedKnowledgeGraphForDocument(target.getId(), owner.getId()).getNodes()).isEmpty();
        active = chunk(target, "v2"); generate();
        assertThat(lookup(target, "java").getId()).isEqualTo(id);
        assertThat(sources.findByConceptIdAndDocumentChunkId(id, active.getId())).isPresent();
        assertThat(queries.getGroundedKnowledgeGraphForDocument(target.getId(), owner.getId()).getEdges()).hasSize(1);
    }
    @Test void concurrentIndexChangeRejectsProviderResultBeforeWriting() {
        when(provider.generate(anyString(), anyString(), anyDouble())).thenAnswer(inv -> {
            target.setActiveIndexVersion("v2"); documents.saveAndFlush(target);
            return "{\"concepts\":[{\"name\":\"Java\",\"evidenceText\":\"Java\"}]}";
        });
        assertThatThrownBy(this::generate).isInstanceOf(KnowledgeGraphStateException.class);
        assertThat(concepts.findByDocumentIdAndNormalizedName(target.getId(), "java")).isEmpty();
    }
    @Test void readNeverCallsProvider() {
        assertThat(queries.getGroundedKnowledgeGraphForDocument(target.getId(), owner.getId()).getNodes()).isEmpty();
        verifyNoInteractions(provider);
    }
    @Test void providerFailureDoesNotChangeReadyIndex() {
        when(provider.generate(anyString(), anyString(), anyDouble())).thenThrow(new RuntimeException("provider failure"));
        assertThatThrownBy(this::generate).isInstanceOf(KnowledgeGraphGenerationException.class);
        Document doc = documents.findById(target.getId()).orElseThrow();
        assertThat(doc.getStatus()).isEqualTo("READY"); assertThat(doc.getActiveIndexVersion()).isEqualTo("v1");
    }
    @Test void legacyTextNeverEntersProviderPrompt() {
        generate(); verify(provider, atLeastOnce()).generate(anyString(), argThat(text -> !text.contains("LEGACY TEXT")), anyDouble());
    }

    @Test void failedRelationProviderRetainsPersistedActiveConcepts() {
        when(provider.generate(startsWith("Extract grounded relations"), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("provider unavailable"));
        var graph = generation.generate(owner.getId(), target.getId());
        assertThat(graph.getGenerationStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(graph.getNodes()).hasSize(2); assertThat(graph.getEdges()).isEmpty();
        assertThat(sources.findByDocumentChunkId(active.getId())).hasSize(2);
    }
    @Test void regeneratedNovelNamesCannotGrowActiveChunkWithoutBound() {
        var scope = KnowledgeScope.capture(target, owner.getId());
        for (int i=0;i<50;i++) conceptService.getOrCreateWithSource(scope, "Name"+i, "", "TERM", active, "Java", .9);
        long conceptCount = concepts.count(), sourceCount = sources.count();
        for (int i=50;i<55;i++) {
            final int name = i;
            assertThatThrownBy(() -> conceptService.getOrCreateWithSource(scope, "Name"+name, "", "TERM", active, "Java", .9))
                    .isInstanceOf(ConceptCandidateLimitException.class);
        }
        assertThat(concepts.count()).isEqualTo(conceptCount);
        assertThat(sources.count()).isEqualTo(sourceCount);
        var reused = conceptService.getOrCreateWithSource(scope, "Name0", "Mô tả mới", "TERM", active, "Java", .9);
        assertThat(reused.getConcept().getDescription()).isEqualTo("Mô tả mới");
        assertThat(sources.findByDocumentChunkId(active.getId())).hasSize(50);
    }

    @Test void legitimateEmptyRelationsKeepUsableSuccess() {
        when(provider.generate(startsWith("Extract grounded relations"), anyString(), anyDouble()))
                .thenReturn("{\"relations\":[]}");
        var graph = generation.generate(owner.getId(), target.getId());
        assertThat(graph.getGenerationStatus()).isEqualTo("SUCCESS");
        assertThat(graph.getNodes()).hasSize(2); assertThat(graph.getEdges()).isEmpty();
    }
    @Test void nonReadyReadsAreBlockedAsWellAsGeneration() {
        target.setStatus("PROCESSING"); documents.saveAndFlush(target);
        assertThatThrownBy(() -> queries.getGroundedKnowledgeGraphForDocument(target.getId(), owner.getId()))
                .isInstanceOf(KnowledgeGraphStateException.class);
        verifyNoInteractions(provider);
    }
}
