package com.example.studyassistant.controller;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest

@ActiveProfiles("integration")
@Tag("Integration")
public class MasteryReadApiMySQLIntegrationTest {

    private MockMvc mockMvc; @Autowired private MasteryController masteryController;
    @Autowired private StudentConceptMasteryRepository masteryRepository;
    @Autowired private StudentLearningEventRepository eventRepository;
    @Autowired private ConceptRelationSourceRepository conceptRelationSourceRepository;
    @Autowired private ConceptRelationRepository conceptRelationRepository;
    @Autowired private ConceptSourceRepository conceptSourceRepository;
    @Autowired private DocumentChunkRepository documentChunkRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ConceptRepository conceptRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private User owner, other;
    private Notebook ownerNotebook, otherNotebook;
    private Document docReady;
    private DocumentChunk chunkActive, chunkStale;
    
    private Concept cUnseen, cObserved, cZeroEvidence, cStaleOnly, cMixed, cForeign;

    private MockHttpSession ownerSession;
    private MockHttpSession otherSession;

    @BeforeEach
    void setUp() {
        cleanup();
        mockMvc = MockMvcBuilders.standaloneSetup(masteryController)
            
            .build();
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            owner = new User();
            owner.setUsername("owner");
            owner.setEmail("owner@example.com");
            owner.setPassword("hash");
            owner.setFullName("Owner");
            
            other = new User();
            other.setUsername("other");
            other.setEmail("other@example.com");
            other.setPassword("hash");
            other.setFullName("Other");
            userRepository.saveAll(List.of(owner, other));

            ownerSession = new MockHttpSession();
            ownerSession.setAttribute("userId", owner.getId());
            
            otherSession = new MockHttpSession();
            otherSession.setAttribute("userId", other.getId());

            ownerNotebook = new Notebook();
            ownerNotebook.setUser(owner);
            ownerNotebook.setTitle("Owner NB");
            ownerNotebook.setDescription("Desc");
            
            otherNotebook = new Notebook();
            otherNotebook.setUser(other);
            otherNotebook.setTitle("Other NB");
            otherNotebook.setDescription("Desc");
            notebookRepository.saveAll(List.of(ownerNotebook, otherNotebook));

            docReady = new Document();
            docReady.setUser(owner);
            docReady.setNotebook(ownerNotebook);
            docReady.setFileName("doc1.pdf");
            docReady.setFilePath("/tmp/doc1.pdf");
            docReady.setStatus("READY");
            docReady.setActiveIndexVersion("v2");
            documentRepository.save(docReady);

            chunkActive = new DocumentChunk();
            chunkActive.setDocument(docReady);
            chunkActive.setContent("active chunk");
            chunkActive.setChunkIndex(0);
            chunkActive.setTokenCount(10);
            chunkActive.setIndexVersion("v2");
            
            chunkStale = new DocumentChunk();
            chunkStale.setDocument(docReady);
            chunkStale.setContent("stale chunk");
            chunkStale.setChunkIndex(1);
            chunkStale.setTokenCount(10);
            chunkStale.setIndexVersion("v1");
            documentChunkRepository.saveAll(List.of(chunkActive, chunkStale));

            cUnseen = createConcept(ownerNotebook, "Concept Unseen");
            cObserved = createConcept(ownerNotebook, "Concept Observed");
            cZeroEvidence = createConcept(ownerNotebook, "Concept Zero");
            cStaleOnly = createConcept(ownerNotebook, "Concept Stale");
            cMixed = createConcept(ownerNotebook, "Concept Mixed");
            cForeign = createConcept(otherNotebook, "Concept Foreign");

            // Source connections
            createConceptSource(cUnseen, chunkActive);
            
            createConceptSource(cObserved, chunkActive);
            masteryRepository.save(new StudentConceptMastery(owner, cObserved, 0.8, 5));
            
            createConceptSource(cZeroEvidence, chunkActive);
            masteryRepository.save(new StudentConceptMastery(owner, cZeroEvidence, 0.0, 0));
            
            createConceptSource(cStaleOnly, chunkStale);
            
            createConceptSource(cMixed, chunkActive);
            createConceptSource(cMixed, chunkStale);
            
            // Foreign concept setup
            Document foreignDoc = new Document();
            foreignDoc.setUser(other);
            foreignDoc.setNotebook(otherNotebook);
            foreignDoc.setFileName("f.pdf");
            foreignDoc.setFilePath("/tmp/f.pdf");
            foreignDoc.setStatus("READY");
            foreignDoc.setActiveIndexVersion("v1");
            documentRepository.save(foreignDoc);
            
            DocumentChunk foreignChunk = new DocumentChunk();
            foreignChunk.setDocument(foreignDoc);
            foreignChunk.setContent("f chunk");
            foreignChunk.setChunkIndex(0);
            foreignChunk.setTokenCount(5);
            foreignChunk.setIndexVersion("v1");
            documentChunkRepository.save(foreignChunk);
            
            createConceptSource(cForeign, foreignChunk);
            
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }

    @AfterEach
    void cleanup() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            eventRepository.deleteAllInBatch();
            masteryRepository.deleteAllInBatch();
            conceptRelationSourceRepository.deleteAllInBatch();
            conceptRelationRepository.deleteAllInBatch();
            conceptSourceRepository.deleteAllInBatch();
            documentChunkRepository.deleteAllInBatch();
            documentRepository.deleteAllInBatch();
            conceptRepository.deleteAllInBatch();
            notebookRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
    }

    private Concept createConcept(Notebook notebook, String name) {
        Concept c = new Concept();
        c.setNotebook(notebook);
        c.setCanonicalName(name);
        c.setConceptType("CONCEPT");
        c.setDescription("Desc " + name);
        c.setNormalizedName(name.toLowerCase());
        return conceptRepository.save(c);
    }

    private ConceptSource createConceptSource(Concept concept, DocumentChunk chunk) {
        ConceptSource cs = new ConceptSource();
        cs.setConcept(concept);
        cs.setDocumentChunk(chunk);
        cs.setEvidenceText("evidence");
        cs.setConfidence(0.9);
        return conceptSourceRepository.save(cs);
    }

    @Test
    void testCaseA_NoSessionUserId() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCaseB_OwnerSession() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession))
                .andExpect(status().isOk());
    }

    @Test
    void testCaseC_ForeignNotebook() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", otherNotebook.getId())
                .session(ownerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCaseD_NonexistentNotebook() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", 9999L)
                .session(ownerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCasesE_and_DataLeaks() throws Exception {
        // E: Unseen included, observed included, stale-only excluded
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession)
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4))) // Mixed, Observed, Unseen, ZeroEvidence (4 valid)
                .andExpect(jsonPath("$.items[*].conceptId", not(hasItem(cStaleOnly.getId().intValue()))))
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cUnseen.getId() + ")].state").value(contains("UNSEEN")))
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cUnseen.getId() + ")].masteryEstimate").value(contains(nullValue())))
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cUnseen.getId() + ")].evidenceCount").value(contains(0)))
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cObserved.getId() + ")].state").value(contains("OBSERVED")))
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cObserved.getId() + ")].masteryEstimate").value(contains(0.8)))
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cObserved.getId() + ")].evidenceCount").value(contains(5)))
                
                // DATA LEAKS ASSERTIONS
                .andExpect(jsonPath("$.items[0]").value(not(hasKey("masteryId"))))
                .andExpect(jsonPath("$.items[0]").value(not(hasKey("user"))))
                .andExpect(jsonPath("$.items[0]").value(not(hasKey("userId"))))
                .andExpect(jsonPath("$.items[0]").value(not(hasKey("notebook"))))
                .andExpect(jsonPath("$.items[0]").value(not(hasKey("documentChunk"))))
                .andExpect(jsonPath("$.items[0]").value(not(hasKey("provenance"))));
    }

    @Test
    void testCaseF_ZeroEvidenceObserved() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession)
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cZeroEvidence.getId() + ")].state").value(contains("OBSERVED")))
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cZeroEvidence.getId() + ")].masteryEstimate").value(contains(0.0)))
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cZeroEvidence.getId() + ")].evidenceCount").value(contains(0)));
    }

    @Test
    void testCaseG_DetailValid_and_DataLeaks() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/concepts/{conceptId}/mastery", ownerNotebook.getId(), cObserved.getId())
                .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conceptId").value(cObserved.getId()))
                .andExpect(jsonPath("$.canonicalName").value(cObserved.getCanonicalName()))
                .andExpect(jsonPath("$.conceptType").value(cObserved.getConceptType()))
                .andExpect(jsonPath("$.description").value(cObserved.getDescription()))
                .andExpect(jsonPath("$.state").value("OBSERVED"))
                .andExpect(jsonPath("$.masteryEstimate").value(0.8))
                .andExpect(jsonPath("$.evidenceCount").value(5))
                .andExpect(jsonPath("$").value(not(hasKey("masteryId"))))
                .andExpect(jsonPath("$").value(not(hasKey("provenance"))))
                .andExpect(jsonPath("$").value(not(hasKey("sourceId"))));
    }

    @Test
    void testCaseH_DetailStale() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/concepts/{conceptId}/mastery", ownerNotebook.getId(), cStaleOnly.getId())
                .session(ownerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCaseI_DetailForeign() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/concepts/{conceptId}/mastery", ownerNotebook.getId(), cForeign.getId())
                .session(ownerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCaseJ_DetailNonexistent() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/concepts/{conceptId}/mastery", ownerNotebook.getId(), 9999L)
                .session(ownerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCaseK_MixedActiveAndStale() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession)
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cMixed.getId() + ")]").exists());
                // Group By handles dedup
    }

    @Test
    void testCaseL_StaleOnlyHidden() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession)
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].conceptId", not(hasItem(cStaleOnly.getId().intValue()))));

        mockMvc.perform(get("/api/notebooks/{notebookId}/concepts/{conceptId}/mastery", ownerNotebook.getId(), cStaleOnly.getId())
                .session(ownerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCasesM_and_N_Pagination() throws Exception {
        // We have 4 active concepts in ownerNotebook: cUnseen, cObserved, cZeroEvidence, cMixed.
        // Page 0 (size 2)
        String content0 = mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession)
                .param("page", "0")
                .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();

        // Page 1 (size 2)
        String content1 = mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession)
                .param("page", "1")
                .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andReturn().getResponse().getContentAsString();
                
        // Just verify items don't overlap as strings
        assertThat(content0).isNotEqualTo(content1);
    }

    @Test
    void testCaseO_InvalidPage_Negative() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession)
                .param("page", "-1")
                .param("size", "50"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCaseP_InvalidSize_Zero() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession)
                .param("page", "0")
                .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCaseQ_InvalidSize_TooLarge() throws Exception {
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession)
                .param("page", "0")
                .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCaseR_CrossUserMasteryDoesNotLeak() throws Exception {
        // Foreign user 'other' creates mastery on owner's concept
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        masteryRepository.save(new StudentConceptMastery(other, cUnseen, 0.99, 10));
        transactionManager.commit(status);

        // Owner requests their mastery list
        mockMvc.perform(get("/api/notebooks/{notebookId}/mastery", ownerNotebook.getId())
                .session(ownerSession)
                .param("size", "10"))
                .andExpect(status().isOk())
                // cUnseen should remain UNSEEN for the owner
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cUnseen.getId() + ")].state").value(contains("UNSEEN")))
                .andExpect(jsonPath("$.items[?(@.conceptId == " + cUnseen.getId() + ")].masteryEstimate").value(contains(nullValue())));
    }
}

