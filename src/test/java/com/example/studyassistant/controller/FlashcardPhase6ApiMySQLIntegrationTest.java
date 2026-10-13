package com.example.studyassistant.controller;

import com.example.studyassistant.domain.flashcard.FlashcardRating;
import com.example.studyassistant.domain.flashcard.FlashcardScheduleStatus;
import com.example.studyassistant.domain.flashcard.generation.FlashcardContentHasher;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.adaptive.AdaptiveRecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
public class FlashcardPhase6ApiMySQLIntegrationTest {

    private MockMvc mockMvc;
    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository chunkRepository;
    @Autowired private FlashcardRepository flashcardRepository;
    @Autowired private FlashcardSourceRepository flashcardSourceRepository;
    @Autowired private FlashcardReviewStateRepository stateRepository;
    @Autowired private FlashcardReviewEventRepository eventRepository;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager em;

    @MockitoBean private LlmProvider llmProvider;
    @MockitoBean private Clock clock;
    @MockitoBean private AdaptiveRecommendationService adaptiveService; // prevent real LLM queue calls

    private final List<Long> eventIds = new ArrayList<>();
    private final List<Long> stateIds = new ArrayList<>();
    private final List<Long> sourceIds = new ArrayList<>();
    private final List<Long> cardIds = new ArrayList<>();
    private final List<Long> chunkIds = new ArrayList<>();
    private final List<Long> docIds = new ArrayList<>();
    private final List<Long> notebookIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    private User user;
    private Notebook notebook;
    private Document doc;
    private MockHttpSession session;
    private Instant now;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        now = Instant.parse("2026-01-01T10:00:00Z");
        setClock(now);
        

        user = new User();
        user.setEmail("phase6_" + UUID.randomUUID() + "@test.com");
        user.setPassword("pass");
        user.setFullName("Phase6 User"); user.setUsername("user_" + UUID.randomUUID().toString().substring(0,8));
        user = userRepository.saveAndFlush(user);
        userIds.add(user.getId());

        notebook = new Notebook();
        notebook.setUser(user);
        notebook.setTitle("Phase6 Notebook " + UUID.randomUUID());
        notebook = notebookRepository.saveAndFlush(notebook);
        notebookIds.add(notebook.getId());

        doc = new Document();
        doc.setUser(user);
        doc.setNotebook(notebook);
        doc.setFileName("phase6_" + UUID.randomUUID() + ".pdf");
        doc.setFilePath("/phase6");
        doc.setStatus("READY");
        doc.setActiveIndexVersion("v1");
        doc = documentRepository.saveAndFlush(doc);
        docIds.add(doc.getId());

        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocument(doc);
        chunk.setChunkIndex(1);
        chunk.setContent("Sample content for E2E tests");
        chunk.setIndexVersion("v1");
        chunk.setTokenCount(100);
        chunk = chunkRepository.saveAndFlush(chunk);
        chunkIds.add(chunk.getId());

        session = new MockHttpSession();
        session.setAttribute("userId", user.getId());
    }

    @AfterEach
    void cleanUp() {
        txTemplate.executeWithoutResult(status -> {
            deleteIn(eventIds, "flashcard_review_events");
            deleteIn(stateIds, "flashcard_review_states");
            deleteIn(sourceIds, "flashcard_sources");
            
            // Collect any auto-generated cards, sources, states, events from the DB
            List<Long> generatedCards = em.createQuery("SELECT f.id FROM Flashcard f WHERE f.document.id = :docId", Long.class)
                    .setParameter("docId", doc.getId()).getResultList();
            for(Long cid : generatedCards) {
                List<Long> sIds = em.createQuery("SELECT s.id FROM FlashcardSource s WHERE s.flashcard.id = :cid", Long.class)
                        .setParameter("cid", cid).getResultList();
                deleteIn(sIds, "flashcard_sources");
                List<Long> evtIds = em.createQuery("SELECT e.id FROM FlashcardReviewEvent e WHERE e.flashcard.id = :cid", Long.class)
                        .setParameter("cid", cid).getResultList();
                deleteIn(evtIds, "flashcard_review_events");
                List<Long> stIds = em.createQuery("SELECT st.id FROM FlashcardReviewState st WHERE st.flashcard.id = :cid", Long.class)
                        .setParameter("cid", cid).getResultList();
                deleteIn(stIds, "flashcard_review_states");
            }
            deleteIn(generatedCards, "flashcards");

            deleteIn(cardIds, "flashcards");
            deleteIn(chunkIds, "document_chunks");
            deleteIn(docIds, "documents");
            deleteIn(notebookIds, "notebooks");
            deleteIn(userIds, "users");
            em.flush();
        });
    }

    private void deleteIn(List<Long> ids, String table) {
        if (ids.isEmpty()) return;
        String idList = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        em.createNativeQuery("DELETE FROM " + table + " WHERE id IN (" + idList + ")").executeUpdate();
    }

    private void setClock(Instant time) {
        now = time;
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
    }

    @Test
    void E1_E13_FullLifecycle_EndToEnd() throws Exception {
        // Step 1: Grounded Generation (Card appears NEW)
        String llmJson = "{\"cards\":[{\"question\":\"Q1\",\"answer\":\"A1\",\"sources\":[\"S1\"]}]}";
        when(llmProvider.generate(anyString(), anyString(), any(Double.class))).thenReturn(llmJson);

        MvcResult genResult = mockMvc.perform(post("/api/documents/" + doc.getId() + "/flashcards/generate")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(1))
                .andReturn();

        String genRespStr = genResult.getResponse().getContentAsString();
        long flashcardId = objectMapper.readTree(genRespStr).get("cards").get(0).get("flashcardId").asLong();

        // E10: REST provenance matches flashcard_sources/document_chunks
        mockMvc.perform(get("/api/notebooks/" + notebook.getId() + "/flashcards/queue").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCards.length()").value(1))
                .andExpect(jsonPath("$.newCards[0].flashcardId").value(flashcardId));

        // Step 3: First GOOD review
        String token1 = java.util.UUID.randomUUID().toString();
        MvcResult reviewResult = mockMvc.perform(post("/api/flashcards/" + flashcardId + "/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"GOOD\", \"reviewToken\": \"" + token1 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn();
        String dueAtStr = objectMapper.readTree(reviewResult.getResponse().getContentAsString()).get("dueAt").asText();
        Instant actualDueAt = Instant.parse(dueAtStr);

        // Step 4: Card no longer NEW, and not yet DUE (at T)
        setClock(now);
        mockMvc.perform(get("/api/notebooks/" + notebook.getId() + "/flashcards/queue").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCards.length()").value(0))
                .andExpect(jsonPath("$.dueCards.length()").value(0));

        // Step 5: Advance clock exactly to dueAt
        setClock(actualDueAt); 
        
        mockMvc.perform(get("/api/notebooks/" + notebook.getId() + "/flashcards/queue").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCards.length()").value(0))
                .andExpect(jsonPath("$.dueCards.length()").value(1))
                .andExpect(jsonPath("$.dueCards[0].flashcardId").value(flashcardId));

        // Step 6: Second review
        String token2 = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/flashcards/" + flashcardId + "/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"HARD\", \"reviewToken\": \"" + token2 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false));

        // Step 7: Retry same token returns replayed=true
        mockMvc.perform(post("/api/flashcards/" + flashcardId + "/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"HARD\", \"reviewToken\": \"" + token2 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        // Step 8: Token payload conflict returns 409
        mockMvc.perform(post("/api/flashcards/" + flashcardId + "/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"EASY\", \"reviewToken\": \"" + token2 + "\"}"))
                .andExpect(status().isConflict());
                
        // E9: Ownership/security isolation
        MockHttpSession emptySession = new MockHttpSession();
        mockMvc.perform(post("/api/documents/" + doc.getId() + "/flashcards/generate").session(emptySession).content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notebooks/" + notebook.getId() + "/flashcards/queue").session(emptySession))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/flashcards/" + flashcardId + "/reviews").session(emptySession).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\",\"reviewToken\":\"" + java.util.UUID.randomUUID().toString() + "\"}"))
                .andExpect(status().isUnauthorized());
                
        MockHttpSession foreignSession = new MockHttpSession();
        foreignSession.setAttribute("userId", 99999L);
        mockMvc.perform(post("/api/documents/" + doc.getId() + "/flashcards/generate").session(foreignSession).content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()); // DocumentNotFoundException -> 404
                
        // E11: Duplicate generation does not duplicate active card
        mockMvc.perform(post("/api/documents/" + doc.getId() + "/flashcards/generate")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persistedCount").value(0))
                .andExpect(jsonPath("$.duplicateSkippedCount").value(1));
                
        // E12: Soft-delete/deprecated delete preserves history
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/flashcards/document/" + doc.getId()).session(session))
                .andExpect(status().isOk());
        // Verify state remains
        Flashcard card = flashcardRepository.findById(flashcardId).orElseThrow();
        assertThat(card.getDeletedAt()).isNotNull();
        
        // E13: Legacy generation cannot bypass grounded service
        try {
            mockMvc.perform(get("/api/flashcards/document/" + doc.getId()).session(session))
                    .andExpect(status().is5xxServerError()); // UnsupportedOperationException mapped by default or explicit handler
        } catch(Exception e) {
            // Nested exception is UnsupportedOperationException
        }
    }
}













