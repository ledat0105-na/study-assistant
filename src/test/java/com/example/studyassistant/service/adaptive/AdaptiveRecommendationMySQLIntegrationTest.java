package com.example.studyassistant.service.adaptive;

import com.example.studyassistant.config.AdaptivePolicyProperties;
import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.dto.adaptive.AdaptiveReasonCode;
import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationDto;
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
public class AdaptiveRecommendationMySQLIntegrationTest {

    @Autowired private AdaptiveRecommendationService recommendationService;
    @Autowired private AdaptiveCandidateService candidateService;
    @Autowired private AdaptivePolicyProperties policyProperties;
    
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
            }
        }
        list.clear();
    }

    private User createUser(String suffix) {
        User u = new User();
        u.setUsername("al142_" + suffix);
        u.setEmail("al142_" + suffix + "@ex.com");
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

    @Test
    void testPolicyDefaults() {
        assertThat(policyProperties.getMasteredThreshold()).isEqualTo(0.80);
        assertThat(policyProperties.getMasteredMinEvidence()).isEqualTo(2);
        assertThat(policyProperties.getDefaultLimit()).isEqualTo(5);
        assertThat(policyProperties.getMaxLimit()).isEqualTo(20);
    }

    @Test
    void testE1_MixedEndToEndRecommendations() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        // A: 0.20 / 5
        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunk);
        createMastery(u, cA, 0.20, 5);

        // B: 1.00 / 1
        Concept cB = createConcept(n, "B");
        createConceptSource(cB, chunk);
        createMastery(u, cB, 1.00, 1);

        // C: UNSEEN
        Concept cC = createConcept(n, "C");
        createConceptSource(cC, chunk);

        // D: 0.90 / 3 (Mastered)
        Concept cD = createConcept(n, "D");
        createConceptSource(cD, chunk);
        createMastery(u, cD, 0.90, 3);

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getConceptId()).isEqualTo(cA.getId());
        assertThat(results.get(1).getConceptId()).isEqualTo(cB.getId());
        assertThat(results.get(2).getConceptId()).isEqualTo(cC.getId());

        assertThat(results.get(0).getReasonCodes()).containsExactly(AdaptiveReasonCode.MASTERY_BELOW_THRESHOLD);
        assertThat(results.get(1).getReasonCodes()).containsExactly(AdaptiveReasonCode.INSUFFICIENT_EVIDENCE_FOR_MASTERY);
        assertThat(results.get(2).getReasonCodes()).containsExactly(AdaptiveReasonCode.UNSEEN_CONCEPT);
    }

    @Test
    void testE2_BothReasons() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunk);
        createMastery(u, cA, 0.50, 1);

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReasonCodes()).containsExactly(
            AdaptiveReasonCode.MASTERY_BELOW_THRESHOLD,
            AdaptiveReasonCode.INSUFFICIENT_EVIDENCE_FOR_MASTERY
        );
    }

    @Test
    void testE3_OneShotPrerequisiteBlocks() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunk);
        createMastery(u, cA, 1.00, 1);

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, chunk);

        ConceptRelation r = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(r, chunk);

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConceptId()).isEqualTo(cA.getId());
    }

    @Test
    void testE4_MasteredPrerequisiteUnblocks() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunk);
        createMastery(u, cA, 0.80, 2);

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, chunk);

        ConceptRelation r = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(r, chunk);

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConceptId()).isEqualTo(cB.getId());
        assertThat(results.get(0).getReasonCodes()).containsExactly(AdaptiveReasonCode.UNSEEN_CONCEPT);
    }

    @Test
    void testE5_MultiplePrerequisites() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunk);
        createMastery(u, cA, 0.90, 5);

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, chunk);
        createMastery(u, cB, 0.50, 1);

        Concept cC = createConcept(n, "C");
        createConceptSource(cC, chunk);

        ConceptRelation r1 = createRelation(cA, cC, RelationType.PREREQUISITE_OF);
        createRelationSource(r1, chunk);
        ConceptRelation r2 = createRelation(cB, cC, RelationType.PREREQUISITE_OF);
        createRelationSource(r2, chunk);

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConceptId()).isEqualTo(cB.getId());
    }

    @Test
    void testE5_MultiplePrerequisites_AllMastered() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunk);
        createMastery(u, cA, 0.90, 5);

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, chunk);
        createMastery(u, cB, 0.85, 3);

        Concept cC = createConcept(n, "C");
        createConceptSource(cC, chunk);

        ConceptRelation r1 = createRelation(cA, cC, RelationType.PREREQUISITE_OF);
        createRelationSource(r1, chunk);
        ConceptRelation r2 = createRelation(cB, cC, RelationType.PREREQUISITE_OF);
        createRelationSource(r2, chunk);

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConceptId()).isEqualTo(cC.getId());
    }

    @Test
    void testE6_StaleConceptCombined() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v2", "READY");
        DocumentChunk staleChunk = createChunk(d, 1, "v1");
        DocumentChunk validChunk = createChunk(d, 2, "v2");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, staleChunk);
        createMastery(u, cA, 0.10, 5);

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, validChunk);
        createMastery(u, cB, 0.10, 5);

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConceptId()).isEqualTo(cB.getId());
    }

    @Test
    void testE7_StaleRelationDoesNotBlock() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v2", "READY");
        DocumentChunk validChunk = createChunk(d, 1, "v2");
        DocumentChunk staleChunk = createChunk(d, 2, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, validChunk);
        createMastery(u, cA, 0.10, 5);

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, validChunk);

        ConceptRelation rAB = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(rAB, staleChunk);

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        assertThat(results).hasSize(2);
        assertThat(results).extracting("conceptId").containsExactlyInAnyOrder(cA.getId(), cB.getId());
    }

    @Test
    void testE8_OwnershipEndToEnd() {
        User ownerUser = createUser("owner_" + UUID.randomUUID().toString().substring(0, 4));
        Notebook n1 = createNotebook(ownerUser, "NB1");
        Document d1 = createDocument(ownerUser, n1, "v1", "READY");
        DocumentChunk c1 = createChunk(d1, 1, "v1");
        Concept cA = createConcept(n1, "A");
        createConceptSource(cA, c1);
        
        User foreignUser = createUser("foreign_" + UUID.randomUUID().toString().substring(0, 4));
        Notebook n3 = createNotebook(foreignUser, "NB3");
        Document d3 = createDocument(foreignUser, n3, "v1", "READY");
        DocumentChunk c3 = createChunk(d3, 1, "v1");
        Concept cC = createConcept(n3, "C");
        createConceptSource(cC, c3);

        List<AdaptiveRecommendationDto> resultsOwner = recommendationService.getRecommendations(ownerUser.getId(), n1.getId(), null);
        assertThat(resultsOwner).hasSize(1);
        assertThat(resultsOwner.get(0).getConceptId()).isEqualTo(cA.getId());

        List<AdaptiveRecommendationDto> resultsForeign = recommendationService.getRecommendations(ownerUser.getId(), n3.getId(), null);
        assertThat(resultsForeign).isEmpty();
    }

    @Test
    void testE9_SCC_CycleEndToEnd() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunk);
        Concept cB = createConcept(n, "B");
        createConceptSource(cB, chunk);

        ConceptRelation rAB = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(rAB, chunk);
        ConceptRelation rBA = createRelation(cB, cA, RelationType.PREREQUISITE_OF);
        createRelationSource(rBA, chunk);

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        
        assertThat(results).hasSize(2);
        assertThat(results).extracting("conceptId").containsExactlyInAnyOrder(cA.getId(), cB.getId());
    }

    @Test
    void testE9_SCC_CycleExternalBlocks() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cX = createConcept(n, "X");
        createConceptSource(cX, chunk);
        
        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunk);
        Concept cB = createConcept(n, "B");
        createConceptSource(cB, chunk);

        ConceptRelation rXA = createRelation(cX, cA, RelationType.PREREQUISITE_OF);
        createRelationSource(rXA, chunk);

        ConceptRelation rAB = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(rAB, chunk);
        ConceptRelation rBA = createRelation(cB, cA, RelationType.PREREQUISITE_OF);
        createRelationSource(rBA, chunk);

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        
        assertThat(results).hasSize(2);
        assertThat(results).extracting("conceptId").containsExactlyInAnyOrder(cX.getId(), cB.getId());
    }

    @Test
    void testE10_AllMastered() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunk);
        createMastery(u, cA, 0.90, 5);
        
        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        assertThat(results).isEmpty();
    }

    @Test
    void testE11_DeterministicRanking() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        
        Document d1 = createDocument(u, n, "v1", "READY");
        Document d2 = createDocument(u, n, "v1", "READY");
        
        DocumentChunk chunkD2C1 = createChunk(d2, 1, "v1");
        DocumentChunk chunkD1C2 = createChunk(d1, 2, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunkD2C1);
        createMastery(u, cA, 0.30, 2);

        Concept cB = createConcept(n, "B");
        createConceptSource(cB, chunkD2C1);
        createMastery(u, cB, 0.30, 5);

        Concept cC = createConcept(n, "C");
        createConceptSource(cC, chunkD1C2); 
        createMastery(u, cC, 0.30, 2);
        
        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getConceptId()).isEqualTo(cB.getId());
        assertThat(results.get(1).getConceptId()).isEqualTo(cC.getId());
        assertThat(results.get(2).getConceptId()).isEqualTo(cA.getId());
        
        List<AdaptiveRecommendationDto> results2 = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        assertThat(results2.get(0).getConceptId()).isEqualTo(cB.getId());
        assertThat(results2.get(1).getConceptId()).isEqualTo(cC.getId());
        assertThat(results2.get(2).getConceptId()).isEqualTo(cA.getId());
    }

    @Test
    void testE12_LimitEndToEnd() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        for (int i = 0; i < 22; i++) {
            Concept c = createConcept(n, "Concept_" + i);
            createConceptSource(c, chunk);
        }

        assertThat(recommendationService.getRecommendations(u.getId(), n.getId(), null)).hasSize(5);
        assertThat(recommendationService.getRecommendations(u.getId(), n.getId(), 0)).hasSize(5);
        assertThat(recommendationService.getRecommendations(u.getId(), n.getId(), -1)).hasSize(5);
        assertThat(recommendationService.getRecommendations(u.getId(), n.getId(), 1)).hasSize(1);
        assertThat(recommendationService.getRecommendations(u.getId(), n.getId(), 20)).hasSize(20);
        assertThat(recommendationService.getRecommendations(u.getId(), n.getId(), 21)).hasSize(20);
    }

    @Test
    void testE13_EmptyNotebook() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        
        assertThat(recommendationService.getRecommendations(u.getId(), n.getId(), null)).isEmpty();
    }

    @Test
    void testE14_ReadOnly() {
        User u = createUser(UUID.randomUUID().toString().substring(0, 8));
        Notebook n = createNotebook(u, "NB");
        Document d = createDocument(u, n, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cA = createConcept(n, "A");
        createConceptSource(cA, chunk);
        createMastery(u, cA, 0.50, 2);
        
        long masteryCountBefore = masteryRepository.count();
        long relationCountBefore = relationRepository.count();
        long sourceCountBefore = conceptSourceRepository.count();

        List<AdaptiveRecommendationDto> results = recommendationService.getRecommendations(u.getId(), n.getId(), null);
        assertThat(results).hasSize(1);

        long masteryCountAfter = masteryRepository.count();
        long relationCountAfter = relationRepository.count();
        long sourceCountAfter = conceptSourceRepository.count();

        assertThat(masteryCountAfter).isEqualTo(masteryCountBefore);
        assertThat(relationCountAfter).isEqualTo(relationCountBefore);
        assertThat(sourceCountAfter).isEqualTo(sourceCountBefore);
    }
}
