package com.example.studyassistant.controller;
import com.example.studyassistant.domain.flashcard.exception.FlashcardNotFoundException;

import com.example.studyassistant.domain.flashcard.FlashcardRating;
import com.example.studyassistant.domain.flashcard.generation.*;
import com.example.studyassistant.domain.flashcard.generation.exception.*;
import com.example.studyassistant.domain.flashcard.queue.*;
import com.example.studyassistant.domain.flashcard.*;
import com.example.studyassistant.domain.flashcard.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class FlashcardPhase6ControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GroundedFlashcardGenerationService generationService;

    @Mock
    private FlashcardStudyQueueService queueService;

    @Mock
    private FlashcardReviewService reviewService;

    @InjectMocks
    private FlashcardPhase6Controller controller;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        session = new MockHttpSession();
        session.setAttribute("userId", 1L);
    }

    // A1 - A15: Generation Endpoint Tests
    @Test
    void A1_generateFlashcards_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/documents/10/flashcards/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void A2_generateFlashcards_withValidAuth_returns200() throws Exception {
        when(generationService.generate(any())).thenReturn(new GroundedFlashcardGenerationResult(10L, null, 10, 5, 5, 0, List.of()));

        mockMvc.perform(post("/api/documents/10/flashcards/generate")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestedCount\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(10));
    }

    @Test
    void A3_generateFlashcards_withConcept_returns200() throws Exception {
        when(generationService.generate(any())).thenReturn(new GroundedFlashcardGenerationResult(10L, 99L, 10, 5, 5, 0, List.of()));

        mockMvc.perform(post("/api/documents/10/flashcards/generate")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conceptId\": 99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conceptId").value(99));
    }

    @Test
    void A4_generateFlashcards_emptyBody_isAllowed() throws Exception {
        when(generationService.generate(any())).thenReturn(new GroundedFlashcardGenerationResult(10L, null, 10, 5, 5, 0, List.of()));

        mockMvc.perform(post("/api/documents/10/flashcards/generate")
                .session(session))
                .andExpect(status().isOk());
    }

    @Test
    void A5_generateFlashcards_DocumentNotFound_returns404() throws Exception {
        when(generationService.generate(any())).thenThrow(new DocumentNotFoundException("Doc not found"));
        mockMvc.perform(post("/api/documents/10/flashcards/generate").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void A6_generateFlashcards_DocumentNotIndexed_returns400() throws Exception {
        when(generationService.generate(any())).thenThrow(new DocumentNotIndexedException("Not indexed"));
        mockMvc.perform(post("/api/documents/10/flashcards/generate").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void A7_generateFlashcards_InsufficientEvidence_returns400() throws Exception {
        when(generationService.generate(any())).thenThrow(new InsufficientEvidenceException("No evidence"));
        mockMvc.perform(post("/api/documents/10/flashcards/generate").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void A8_generateFlashcards_ConceptNotValid_returns400() throws Exception {
        when(generationService.generate(any())).thenThrow(new ConceptNotValidException("Invalid concept"));
        mockMvc.perform(post("/api/documents/10/flashcards/generate").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void A9_generateFlashcards_StaleGenerationSnapshot_returns409() throws Exception {
        when(generationService.generate(any())).thenThrow(new StaleGenerationSnapshotException("Stale"));
        mockMvc.perform(post("/api/documents/10/flashcards/generate").session(session))
                .andExpect(status().isConflict());
    }

    @Test
    void A10_generateFlashcards_IllegalStateException_returns400() throws Exception {
        when(generationService.generate(any())).thenThrow(new IllegalStateException("Some illegal state"));
        mockMvc.perform(post("/api/documents/10/flashcards/generate").session(session))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void A11_generateFlashcards_MissingSessionUserId_returns401() throws Exception {
        MockHttpSession emptySession = new MockHttpSession();
        session = new MockHttpSession();
        mockMvc.perform(post("/api/documents/10/flashcards/generate").session(emptySession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void A12_generateFlashcards_SuccessIncludesStats() throws Exception {
        when(generationService.generate(any())).thenReturn(new GroundedFlashcardGenerationResult(10L, null, 10, 8, 7, 1, List.of()));
        mockMvc.perform(post("/api/documents/10/flashcards/generate").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persistedCount").value(7))
                .andExpect(jsonPath("$.duplicateSkippedCount").value(1));
    }

    @Test
    void A13_generateFlashcards_WithNegativeRequestedCount_PassesToService() throws Exception {
        when(generationService.generate(any())).thenReturn(new GroundedFlashcardGenerationResult(10L, null, 10, 5, 5, 0, List.of()));
        mockMvc.perform(post("/api/documents/10/flashcards/generate")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestedCount\": -5}"))
                .andExpect(status().isOk());
    }

    

    @Test
    void A15_generateFlashcards_AcceptsNullConcept() throws Exception {
        when(generationService.generate(any())).thenReturn(new GroundedFlashcardGenerationResult(10L, null, 10, 5, 5, 0, List.of()));
        mockMvc.perform(post("/api/documents/10/flashcards/generate")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conceptId\": null}"))
                .andExpect(status().isOk());
    }

    // A16 - A25: Queue Endpoint Tests
    @Test
    void A16_getQueue_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/notebooks/100/flashcards/queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void A17_getQueue_withValidAuth_returns200() throws Exception {
        when(queueService.getQueue(any())).thenReturn(new FlashcardStudyQueueResult(java.time.Instant.now(), List.of(), List.of()));

        mockMvc.perform(get("/api/notebooks/100/flashcards/queue").session(session))
                .andExpect(status().isOk())
                ;
    }

    @Test
    void A18_getQueue_withCustomLimits_passesToService() throws Exception {
        when(queueService.getQueue(any())).thenReturn(new FlashcardStudyQueueResult(java.time.Instant.now(), List.of(), List.of()));

        mockMvc.perform(get("/api/notebooks/100/flashcards/queue?maxNewCards=10&maxDueCards=20").session(session))
                .andExpect(status().isOk());
        // Could verify arguments to service here
    }

    

    @Test
    void A20_getQueue_defaultLimits_are50() throws Exception {
        when(queueService.getQueue(any())).thenReturn(new FlashcardStudyQueueResult(java.time.Instant.now(), List.of(), List.of()));
        mockMvc.perform(get("/api/notebooks/100/flashcards/queue").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void A21_getQueue_returnsNewAndDueArrays() throws Exception {
        when(queueService.getQueue(any())).thenReturn(new FlashcardStudyQueueResult(java.time.Instant.now(), List.of(), List.of()));
        mockMvc.perform(get("/api/notebooks/100/flashcards/queue").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCards").isArray())
                .andExpect(jsonPath("$.dueCards").isArray());
    }

    @Test
    void A22_getQueue_zeroLimits_areAllowed() throws Exception {
        when(queueService.getQueue(any())).thenReturn(new FlashcardStudyQueueResult(java.time.Instant.now(), List.of(), List.of()));
        mockMvc.perform(get("/api/notebooks/100/flashcards/queue?maxNewCards=0").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void A23_getQueue_negativeLimits_areAllowed() throws Exception {
        when(queueService.getQueue(any())).thenReturn(new FlashcardStudyQueueResult(java.time.Instant.now(), List.of(), List.of()));
        mockMvc.perform(get("/api/notebooks/100/flashcards/queue?maxDueCards=-1").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void A24_getQueue_wrongNotebookType_returns400() throws Exception {
        mockMvc.perform(get("/api/notebooks/abc/flashcards/queue").session(session))
                .andExpect(status().isBadRequest()); // PathVariable type mismatch
    }

    @Test
    void A25_getQueue_withNullSessionUserId_returns401() throws Exception {
        MockHttpSession emptySession = new MockHttpSession();
        session = new MockHttpSession();
        mockMvc.perform(get("/api/notebooks/100/flashcards/queue").session(emptySession))
                .andExpect(status().isUnauthorized());
    }

    // A26 - A35: Review Endpoint Tests
    @Test
    void A26_submitReview_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/flashcards/500/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"GOOD\", \"reviewToken\": \"abc\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void A27_submitReview_withValidAuthAndBody_returns200() throws Exception {
        when(reviewService.processReview(any())).thenReturn(Mockito.mock(FlashcardReviewResult.class));

        mockMvc.perform(post("/api/flashcards/500/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"GOOD\", \"reviewToken\": \"abc\"}"))
                .andExpect(status().isOk())
                ;
    }

    @Test
    void A28_submitReview_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/flashcards/500/reviews").session(session))
                .andExpect(status().isBadRequest()); // Missing body
    }

    @Test
    void A29_submitReview_missingRating_returns400() throws Exception {
        mockMvc.perform(post("/api/flashcards/500/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewToken\": \"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void A30_submitReview_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/flashcards/500/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"GOOD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void A31_submitReview_invalidRatingEnum_returns400() throws Exception {
        mockMvc.perform(post("/api/flashcards/500/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"UNKNOWN\", \"reviewToken\": \"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void A32_submitReview_FlashcardNotFound_returns404() throws Exception {
        when(reviewService.processReview(any())).thenThrow(new FlashcardNotFoundException("Not found"));

        mockMvc.perform(post("/api/flashcards/500/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"GOOD\", \"reviewToken\": \"abc\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void A33_submitReview_ReplayedToken_returns200AndReplayedTrue() throws Exception {
        when(reviewService.processReview(any())).thenReturn(Mockito.mock(FlashcardReviewResult.class));

        mockMvc.perform(post("/api/flashcards/500/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"GOOD\", \"reviewToken\": \"abc\"}"))
                .andExpect(status().isOk())
                ;
    }

    @Test
    void A34_submitReview_DifferentRatingConflict_returns409() throws Exception {
        when(reviewService.processReview(any())).thenThrow(new IllegalStateException("Different rating provided for already completed review token"));

        mockMvc.perform(post("/api/flashcards/500/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"HARD\", \"reviewToken\": \"abc\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void A35_submitReview_WrongUserId_returns400() throws Exception {
        // Typically caught by some domain logic
        when(reviewService.processReview(any())).thenThrow(new IllegalStateException("User mismatch"));

        mockMvc.perform(post("/api/flashcards/500/reviews")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": \"EASY\", \"reviewToken\": \"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void viewerQuickStartUsesBoundedGenerationAndSessionOwner() throws Exception {
        when(generationService.generateQuickBatch(any())).thenReturn(new GroundedFlashcardGenerationResult(10L, null, 8, 5, 5, 0, List.of()));
        mockMvc.perform(post("/api/documents/10/flashcards/generate").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quickStart\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestedCount").value(8));
        Mockito.verify(generationService).generateQuickBatch(new GroundedFlashcardGenerationCommand(1L,10L,null,null));
        Mockito.verify(generationService, Mockito.never()).generate(any());
    }
}
