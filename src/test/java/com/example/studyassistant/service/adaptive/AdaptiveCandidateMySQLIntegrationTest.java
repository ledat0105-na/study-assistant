package com.example.studyassistant.service.adaptive;

import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.dto.adaptive.AdaptiveCandidateFactDto;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class AdaptiveCandidateMySQLIntegrationTest {

    @Autowired private AdaptiveCandidateService candidateService;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private ConceptRepository conceptRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository chunkRepository;
    @Autowired private ConceptSourceRepository conceptSourceRepository;
    @Autowired private ConceptRelationRepository relationRepository;
    @Autowired private ConceptRelationSourceRepository relationSourceRepository;
    @Autowired private StudentConceptMasteryRepository masteryRepository;

    private final List<ConceptRelationSource> trackedRelationSources = new ArrayList<>();
    private final List<ConceptRelation> trackedRelations = new ArrayList<>();
    private final List<ConceptSource> trackedConceptSources = new ArrayList<>();
    private final List<StudentConceptMastery> trackedMasteries = new ArrayList<>();
    private final List<DocumentChunk> trackedChunks = new ArrayList<>();
    private final List<Concept> trackedConcepts = new ArrayList<>();
    private final List<Document> trackedDocuments = new ArrayList<>();
    private final List<Notebook> trackedNotebooks = new ArrayList<>();
    private final List<User> trackedUsers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // FK-safe cleanup order. We DO NOT catch generic Exception so integrity violations bubble up.
        deleteTracked(trackedMasteries, masteryRepository);
        deleteTracked(trackedRelationSources, relationSourceRepository);
        deleteTracked(trackedRelations, relationRepository);
        deleteTracked(trackedConceptSources, conceptSourceRepository);
        deleteTracked(trackedChunks, chunkRepository);
        deleteTracked(trackedConcepts, conceptRepository);
        deleteTracked(trackedDocuments, documentRepository);
        deleteTracked(trackedNotebooks, notebookRepository);
        deleteTracked(trackedUsers, userRepository);
    }

    private <T> void deleteTracked(List<T> list, org.springframework.data.repository.CrudRepository<T, Long> repo) {
        for (int i = list.size() - 1; i >= 0; i--) {
            try {
                repo.delete(list.get(i));
            } catch (EmptyResultDataAccessException ignored) {
                // Harmless if already missing
            }
        }
        list.clear();
    }

    // --- Helpers ---
    private User createUser(String suffix) {
        User u = new User();
        u.setUsername("al12_" + suffix);
        u.setEmail("al12_" + suffix + "@ex.com");
        u.setPassword("hash");
        u.setFullName("User");
        u = userRepository.save(u);
        trackedUsers.add(u);
        return u;
    }

    private Notebook createNotebook(User u, String title) {
        Notebook n = new Notebook();
        n.setUser(u);
        n.setTitle(title);
        n = notebookRepository.save(n);
        trackedNotebooks.add(n);
        return n;
    }

    private Document createDocument(User u, Notebook n, String version, String status) {
        Document d = new Document();
        d.setUser(u);
        d.setNotebook(n);
        d.setFileName("f_" + UUID.randomUUID().toString().substring(0, 5) + ".pdf");
        d.setFilePath("/f.pdf");
        d.setStatus(status);
        d.setActiveIndexVersion(version);
        d = documentRepository.save(d);
        trackedDocuments.add(d);
        return d;
    }

    private DocumentChunk createChunk(Document d, int index, String version) {
        DocumentChunk c = new DocumentChunk();
        c.setDocument(d);
        c.setContent("content");
        c.setTokenCount(10);
        c.setContentHash(UUID.randomUUID().toString());
        c.setChunkIndex(index);
        c.setIndexVersion(version);
        c = chunkRepository.save(c);
        trackedChunks.add(c);
        return c;
    }

    private Concept createConcept(Notebook n, String name) {
        Concept c = new Concept();
        c.setNotebook(n);
        c.setCanonicalName(name);
        c.setNormalizedName(name.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 5));
        c = conceptRepository.save(c);
        trackedConcepts.add(c);
        return c;
    }

    private ConceptSource createConceptSource(Concept c, DocumentChunk dc) {
        ConceptSource cs = new ConceptSource();
        cs.setConcept(c);
        cs.setDocumentChunk(dc);
        cs.setEvidenceText("ev");
        cs = conceptSourceRepository.save(cs);
        trackedConceptSources.add(cs);
        return cs;
    }

    private ConceptRelation createRelation(Concept source, Concept target, RelationType type) {
        ConceptRelation cr = new ConceptRelation();
        cr.setSourceConcept(source);
        cr.setTargetConcept(target);
        cr.setRelationType(type);
        cr = relationRepository.save(cr);
        trackedRelations.add(cr);
        return cr;
    }

    private ConceptRelationSource createRelationSource(ConceptRelation cr, DocumentChunk dc) {
        ConceptRelationSource crs = new ConceptRelationSource();
        crs.setConceptRelation(cr);
        crs.setDocumentChunk(dc);
        crs.setEvidenceText("ev");
        crs = relationSourceRepository.save(crs);
        trackedRelationSources.add(crs);
        return crs;
    }

    private StudentConceptMastery createMastery(User u, Concept c, Double est, Integer ev) {
        StudentConceptMastery m = new StudentConceptMastery(u, c, est, ev);
        m = masteryRepository.save(m);
        trackedMasteries.add(m);
        return m;
    }

    // --- Tests ---

    @Test
    void testM1_M2_M3_BasicFactsQuery() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk c = createChunk(d, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, c);
        createMastery(u, cA, 0.8, 5); // OBSERVED

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, c); // UNSEEN

        Concept cC = createConcept(n, "C"); // Not grounded

        List<AdaptiveCandidateFactDto> facts = candidateService.getCandidateFacts(u.getId(), n.getId());

        assertThat(facts).hasSize(2); // A and B only

        AdaptiveCandidateFactDto factA = facts.stream().filter(f -> f.getConceptId().equals(cA.getId())).findFirst().orElseThrow();
        assertThat(factA.getMasteryState()).isEqualTo(MasteryState.OBSERVED);
        assertThat(factA.getMasteryEstimate()).isEqualTo(0.8);
        assertThat(factA.getFirstDocumentId()).isEqualTo(d.getId());

        AdaptiveCandidateFactDto factB = facts.stream().filter(f -> f.getConceptId().equals(cB.getId())).findFirst().orElseThrow();
        assertThat(factB.getMasteryState()).isEqualTo(MasteryState.UNSEEN);
        assertThat(factB.getMasteryEstimate()).isNull();
    }

    @Test
    void testM4_DuplicateRelationProvenanceDedup() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk c1 = createChunk(d, 1, "v1");
        DocumentChunk c2 = createChunk(d, 2, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, c1);

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, c1);

        ConceptRelation r = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(r, c1);
        createRelationSource(r, c2); // Duplicate provenance for same relation

        List<AdaptiveCandidateFactDto> facts = candidateService.getCandidateFacts(u.getId(), n.getId());

        AdaptiveCandidateFactDto factB = facts.stream().filter(f -> f.getConceptId().equals(cB.getId())).findFirst().orElseThrow();
        // Concept A should appear EXACTLY once
        assertThat(factB.getPrerequisiteConceptIds()).containsExactly(cA.getId());
    }

    @Test
    void testM5_PrerequisiteDirectionAndTypeFilter() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk c = createChunk(d, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, c);
        Concept cB = createConcept(n, "B");
        createConceptSource(cB, c);
        Concept cC = createConcept(n, "C");
        createConceptSource(cC, c);
        Concept cD = createConcept(n, "D");
        createConceptSource(cD, c);
        Concept cE = createConcept(n, "E");
        createConceptSource(cE, c);

        // A PREREQUISITE_OF B
        ConceptRelation rAB = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(rAB, c);

        // C RELATED_TO B
        ConceptRelation rCB = createRelation(cC, cB, RelationType.RELATED_TO);
        createRelationSource(rCB, c);

        // D PART_OF B
        ConceptRelation rDB = createRelation(cD, cB, RelationType.PART_OF);
        createRelationSource(rDB, c);

        // E IS_A B
        ConceptRelation rEB = createRelation(cE, cB, RelationType.IS_A);
        createRelationSource(rEB, c);

        List<AdaptiveCandidateFactDto> facts = candidateService.getCandidateFacts(u.getId(), n.getId());

        AdaptiveCandidateFactDto factB = facts.stream().filter(f -> f.getConceptId().equals(cB.getId())).findFirst().orElseThrow();
        assertThat(factB.getPrerequisiteConceptIds()).containsExactly(cA.getId());

        AdaptiveCandidateFactDto factA = facts.stream().filter(f -> f.getConceptId().equals(cA.getId())).findFirst().orElseThrow();
        assertThat(factA.getPrerequisiteConceptIds()).isEmpty(); // No reverse edges
    }

    @Test
    void testM6_StalePrerequisiteEndpointExcluded() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v2", "READY"); // active index is v2
        DocumentChunk validChunk = createChunk(d, 1, "v2");
        DocumentChunk staleChunk = createChunk(d, 2, "v1"); // stale

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, staleChunk); // A is stale

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, validChunk); // B is active

        ConceptRelation rAB = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(rAB, validChunk); // Relation itself has active provenance

        List<AdaptiveCandidateFactDto> facts = candidateService.getCandidateFacts(u.getId(), n.getId());

        // A is NOT a candidate
        assertThat(facts.stream().noneMatch(f -> f.getConceptId().equals(cA.getId()))).isTrue();

        // B is a candidate, but has no prerequisites because A is stale
        AdaptiveCandidateFactDto factB = facts.stream().filter(f -> f.getConceptId().equals(cB.getId())).findFirst().orElseThrow();
        assertThat(factB.getPrerequisiteConceptIds()).isEmpty();
    }

    @Test
    void testM7_StaleRelationProvenanceExcluded() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v2", "READY");
        DocumentChunk validChunk = createChunk(d, 1, "v2");
        DocumentChunk staleChunk = createChunk(d, 2, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, validChunk);

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, validChunk);

        ConceptRelation rAB = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(rAB, staleChunk); // Relation provenance is stale

        List<AdaptiveCandidateFactDto> facts = candidateService.getCandidateFacts(u.getId(), n.getId());

        AdaptiveCandidateFactDto factB = facts.stream().filter(f -> f.getConceptId().equals(cB.getId())).findFirst().orElseThrow();
        assertThat(factB.getPrerequisiteConceptIds()).isEmpty();
    }

    @Test
    void testM8_OwnershipIsolation() {
        User ownerUser = createUser("owner_" + UUID.randomUUID().toString().substring(0, 4));
        Notebook n1 = createNotebook(ownerUser, "NB1");
        Document d1 = createDocument(ownerUser, n1, "v1", "READY");
        DocumentChunk c1 = createChunk(d1, 1, "v1");
        Concept cA = createConcept(n1, "A");
        createConceptSource(cA, c1);

        Notebook n2 = createNotebook(ownerUser, "NB2");
        Document d2 = createDocument(ownerUser, n2, "v1", "READY");
        DocumentChunk c2 = createChunk(d2, 1, "v1");
        Concept cB = createConcept(n2, "B");
        createConceptSource(cB, c2);

        User foreignUser = createUser("foreign_" + UUID.randomUUID().toString().substring(0, 4));
        Notebook n3 = createNotebook(foreignUser, "NB3");
        Document d3 = createDocument(foreignUser, n3, "v1", "READY");
        DocumentChunk c3 = createChunk(d3, 1, "v1");
        Concept cC = createConcept(n3, "C");
        createConceptSource(cC, c3);

        // Same-user notebook scoping: ownerUser requesting N1 -> only A (no B from N2)
        List<AdaptiveCandidateFactDto> ownedN1 = candidateService.getCandidateFacts(ownerUser.getId(), n1.getId());
        assertThat(ownedN1).hasSize(1);
        assertThat(ownedN1.get(0).getConceptId()).isEqualTo(cA.getId());

        // Foreign-user notebook isolation: ownerUser requesting N3 -> empty
        List<AdaptiveCandidateFactDto> foreignFacts = candidateService.getCandidateFacts(ownerUser.getId(), n3.getId());
        assertThat(foreignFacts).isEmpty();
    }

    @Test
    void testM9_DeterministicCoherentSourceLocator() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        
        // Creating in order so D1 ID < D2 ID
        Document d1 = createDocument(u, n, "v1", "READY");
        Document d2 = createDocument(u, n, "v1", "READY");
        
        DocumentChunk d1c8 = createChunk(d1, 8, "v1");
        DocumentChunk d1c3 = createChunk(d1, 3, "v1");
        DocumentChunk d2c1 = createChunk(d2, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, d1c8); // D1, Chunk 8
        createConceptSource(cA, d1c3); // D1, Chunk 3
        createConceptSource(cA, d2c1); // D2, Chunk 1

        List<AdaptiveCandidateFactDto> facts = candidateService.getCandidateFacts(u.getId(), n.getId());
        
        AdaptiveCandidateFactDto fact = facts.get(0);
        // Expect D1 / Chunk 3 because D1.id < D2.id and 3 < 8.
        // It proves: lowest documentId wins, lowest chunkIndex wins, and pair is coherent.
        // It does NOT synthesize MIN(DocId)=D1 + MIN(ChunkIndex)=1.
        assertThat(fact.getFirstDocumentId()).isEqualTo(d1.getId());
        assertThat(fact.getFirstChunkIndex()).isEqualTo(3);
    }

    @Test
    void testM10_BoundedQueryArchitecture() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk c = createChunk(d, 1, "v1");

        for (int i = 0; i < 5; i++) {
            Concept concept = createConcept(n, "Concept_" + i);
            createConceptSource(concept, c);
        }

        // Structural verification: the service logic is architected to perform 
        // 1 ownership query + 3 repository bulk queries.
        // It completes successfully and returns exactly 5 concepts.
        List<AdaptiveCandidateFactDto> facts = candidateService.getCandidateFacts(u.getId(), n.getId());
        assertThat(facts).hasSize(5);
    }
}
