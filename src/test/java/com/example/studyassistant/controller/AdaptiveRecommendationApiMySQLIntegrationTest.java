package com.example.studyassistant.controller;

import com.example.studyassistant.config.AdaptivePolicyProperties;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class AdaptiveRecommendationApiMySQLIntegrationTest {

    @Autowired private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;

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
    @Autowired private StudentLearningEventRepository eventRepository;

    private final List<StudentConceptMastery> trackedMasteries = new ArrayList<>();
    private final List<ConceptRelationSource> trackedRelationSources = new ArrayList<>();
    private final List<ConceptRelation> trackedRelations = new ArrayList<>();
    private final List<ConceptSource> trackedConceptSources = new ArrayList<>();
    private final List<DocumentChunk> trackedChunks = new ArrayList<>();
    private final List<Concept> trackedConcepts = new ArrayList<>();
    private final List<Document> trackedDocuments = new ArrayList<>();
    private final List<Notebook> trackedNotebooks = new ArrayList<>();
    private final List<User> trackedUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

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
        u.setUsername("al153_" + suffix);
        u.setEmail("al153_" + suffix + "@ex.com");
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

    private Concept buildActiveConcept(Notebook notebook, User user, String name) {
        Document d = createDocument(user, notebook, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");
        Concept c = createConcept(notebook, name);
        createConceptSource(c, chunk);
        return c;
    }

    // ====================================================================================
    // TESTS
    // ====================================================================================

    @Test
    void testE1_MixedRecommendations() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook nb = createNotebook(user, "Mixed");

        Concept cA = buildActiveConcept(nb, user, "A");
        createMastery(user, cA, 0.20, 5);

        Concept cB = buildActiveConcept(nb, user, "B");
        createMastery(user, cB, 1.00, 1);

        Concept cC = buildActiveConcept(nb, user, "C"); // UNSEEN

        Concept cD = buildActiveConcept(nb, user, "D");
        createMastery(user, cD, 0.90, 3); // Mastered

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyVersion").value("ADAPTIVE_V1"))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.recommendations[0].conceptId").value(cA.getId()))
                .andExpect(jsonPath("$.recommendations[0].reasonCodes[0]").value("MASTERY_BELOW_THRESHOLD"))
                .andExpect(jsonPath("$.recommendations[1].conceptId").value(cB.getId()))
                .andExpect(jsonPath("$.recommendations[1].reasonCodes[0]").value("INSUFFICIENT_EVIDENCE_FOR_MASTERY"))
                .andExpect(jsonPath("$.recommendations[2].conceptId").value(cC.getId()))
                .andExpect(jsonPath("$.recommendations[2].reasonCodes[0]").value("UNSEEN_CONCEPT"))
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cD.getId() + ")]").doesNotExist());
    }

    @Test
    void testE2_OneShotPrerequisiteBlock() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook nb = createNotebook(user, "NB");
        Document d = createDocument(user, nb, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cA = createConcept(nb, "A");
        createConceptSource(cA, chunk);
        createMastery(user, cA, 1.0, 1); // Not mastered yet (needs 2)

        Concept cB = createConcept(nb, "B");
        createConceptSource(cB, chunk);

        ConceptRelation r = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(r, chunk);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cA.getId() + ")]").exists())
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cB.getId() + ")]").doesNotExist());
    }

    @Test
    void testE3_MasteredPrerequisiteUnblocks() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook nb = createNotebook(user, "NB");
        Document d = createDocument(user, nb, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");

        Concept cA = createConcept(nb, "A");
        createConceptSource(cA, chunk);
        createMastery(user, cA, 0.80, 2); // Mastered

        Concept cB = createConcept(nb, "B");
        createConceptSource(cB, chunk);

        ConceptRelation r = createRelation(cA, cB, RelationType.PREREQUISITE_OF);
        createRelationSource(r, chunk);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cA.getId() + ")]").doesNotExist()) // A is mastered
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cB.getId() + ")]").exists()) // B is unblocked
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cB.getId() + ")].masteryState").value("UNSEEN"))
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cB.getId() + ")].masteryEstimate").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue()))) // null
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cB.getId() + ")].evidenceCount").value(0))
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cB.getId() + ")].reasonCodes[0]").value("UNSEEN_CONCEPT"));
    }

    @Test
    void testE4_ForeignMasking() throws Exception {
        User ownerUser = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook ownerNb = createNotebook(ownerUser, "Owner");
        buildActiveConcept(ownerNb, ownerUser, "A");

        User foreignUser = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook foreignNb = createNotebook(foreignUser, "Foreign");
        buildActiveConcept(foreignNb, foreignUser, "B");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", ownerUser.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations", foreignNb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    @Test
    void testE5_MissingMasking() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/999999/recommendations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    @Test
    void testE6_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/notebooks/1/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testE7_LimitBindingAndNormalization() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook nb = createNotebook(user, "NB");
        Document d = createDocument(user, nb, "v1", "READY");
        DocumentChunk chunk = createChunk(d, 1, "v1");
        
        for (int i = 0; i < 22; i++) {
            Concept c = createConcept(nb, "C" + i);
            createConceptSource(c, chunk);
        }

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));

        mockMvc.perform(get("/api/notebooks/{id}/recommendations?limit=1", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/notebooks/{id}/recommendations?limit=20", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(20));

        mockMvc.perform(get("/api/notebooks/{id}/recommendations?limit=21", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(20));

        mockMvc.perform(get("/api/notebooks/{id}/recommendations?limit=0", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));

        mockMvc.perform(get("/api/notebooks/{id}/recommendations?limit=-1", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    void testE8_MalformedLimit() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/1/recommendations?limit=abc").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testE9_MalformedNotebookId() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/abc/recommendations").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testE10_InternalFieldAbsence() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook nb = createNotebook(user, "NB");
        buildActiveConcept(nb, user, "A");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].firstDocumentId").doesNotExist())
                .andExpect(jsonPath("$.recommendations[0].firstChunkIndex").doesNotExist())
                .andExpect(jsonPath("$.recommendations[0].lastEvidenceAt").doesNotExist())
                .andExpect(jsonPath("$.recommendations[0].prerequisiteConceptIds").doesNotExist())
                .andExpect(jsonPath("$.recommendations[0].userId").doesNotExist())
                .andExpect(jsonPath("$.recommendations[0].notebookId").doesNotExist())
                .andExpect(jsonPath("$.recommendations[0].masteredThreshold").doesNotExist());
    }

    @Test
    void testE11_UserIdOverride() throws Exception {
        User ownerUser = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook ownerNb = createNotebook(ownerUser, "Owner");
        buildActiveConcept(ownerNb, ownerUser, "A");

        User foreignUser = createUser(UUID.randomUUID().toString().substring(0,8));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", ownerUser.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations?userId=" + foreignUser.getId(), ownerNb.getId())
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void testE12_EmptyOwnedNotebook() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook nb = createNotebook(user, "NB");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    @Test
    void testE13_StaleConceptEndToEnd() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook nb = createNotebook(user, "NB");

        Document d = createDocument(user, nb, "v2", "READY"); // active version is v2
        DocumentChunk chunkV1 = createChunk(d, 1, "v1");
        DocumentChunk chunkV2 = createChunk(d, 2, "v2");

        Concept cA = createConcept(nb, "A");
        createConceptSource(cA, chunkV1); // STALE (chunk v1 != doc v2)
        createMastery(user, cA, 0.1, 1);

        Concept cB = createConcept(nb, "B");
        createConceptSource(cB, chunkV2); // ACTIVE
        createMastery(user, cB, 0.1, 1);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cA.getId() + ")]").doesNotExist())
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cB.getId() + ")]").exists());
    }

    @Test
    void testE14_StaleRelationEndToEnd() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook nb = createNotebook(user, "NB");

        Document d = createDocument(user, nb, "v2", "READY"); // active version is v2
        DocumentChunk chunkV1 = createChunk(d, 1, "v1"); // Stale
        DocumentChunk chunkV2 = createChunk(d, 2, "v2"); // Active

        Concept cA = createConcept(nb, "A");
        createConceptSource(cA, chunkV2);
        createMastery(user, cA, 0.1, 1); // Not mastered

        Concept cB = createConcept(nb, "B");
        createConceptSource(cB, chunkV2);
        
        ConceptRelation r = createRelation(cB, cA, RelationType.PREREQUISITE_OF);
        createRelationSource(r, chunkV1); // STALE EDGE

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations", nb.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[?(@.conceptId == " + cB.getId() + ")]").exists());
    }

    @Test
    void testE15_ReadOnlyHttp() throws Exception {
        User user = createUser(UUID.randomUUID().toString().substring(0,8));
        Notebook nb = createNotebook(user, "NB");
        buildActiveConcept(nb, user, "A");

        long startMastery = masteryRepository.count();
        long startEvent = eventRepository.count();
        long startRel = relationRepository.count();
        long startSrc = conceptSourceRepository.count();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", user.getId());

        mockMvc.perform(get("/api/notebooks/{id}/recommendations", nb.getId()).session(session))
                .andExpect(status().isOk());

        assertThat(masteryRepository.count()).isEqualTo(startMastery);
        assertThat(eventRepository.count()).isEqualTo(startEvent);
        assertThat(relationRepository.count()).isEqualTo(startRel);
        assertThat(conceptSourceRepository.count()).isEqualTo(startSrc);
    }
}
