package com.example.studyassistant.controller.tutor;

import com.example.studyassistant.dto.tutor.TutorMessageRequest;
import com.example.studyassistant.service.tutor.TutorMode;
import com.example.studyassistant.service.tutor.TutorRequestedMode;
import com.example.studyassistant.service.tutor.TutorTurnCommand;
import com.example.studyassistant.service.tutor.TutorTurnResult;
import com.example.studyassistant.service.tutor.TutorTurnService;
import com.example.studyassistant.retrieval.model.CitationDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TutorControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TutorTurnService tutorTurnService;

    @InjectMocks
    private TutorController tutorController;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tutorController).build();
        session = new MockHttpSession();
        session.setAttribute("userId", 1L);
    }

    @Test
    void c1_authenticatedExplain_Returns200() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", null, TutorRequestedMode.EXPLAIN);
        TutorTurnResult result = new TutorTurnResult(10L, 20L, 20L, TutorMode.EXPLAIN, "Ans", true, Collections.emptyList());
        
        when(tutorTurnService.turn(any(TutorTurnCommand.class))).thenReturn(result);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("EXPLAIN"));
    }
    
    @Test
    void c2_c3_authenticatedAskQuestionGiveHint_Returns200() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", null, TutorRequestedMode.ASK_QUESTION);
        TutorTurnResult result = new TutorTurnResult(10L, 20L, 20L, TutorMode.ASK_QUESTION, "Ans", true, Collections.emptyList());
        
        when(tutorTurnService.turn(any(TutorTurnCommand.class))).thenReturn(result);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("ASK_QUESTION"));
                
        request.setRequestedMode(TutorRequestedMode.GIVE_HINT);
        TutorTurnResult result2 = new TutorTurnResult(10L, 20L, 20L, TutorMode.GIVE_HINT, "Ans", true, Collections.emptyList());
        when(tutorTurnService.turn(any(TutorTurnCommand.class))).thenReturn(result2);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("GIVE_HINT"));
    }

    @Test
    void c4_noSession_Returns401() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", null, null);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
                
        verify(tutorTurnService, never()).turn(any());
    }

    @Test
    void c5_sessionNoUserId_Returns401() throws Exception {
        MockHttpSession emptySession = new MockHttpSession();
        TutorMessageRequest request = new TutorMessageRequest("Help", null, null);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(emptySession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
                
        verify(tutorTurnService, never()).turn(any());
    }

    @Test
    void c6_omittedRequestedMode_BecomesNullForAuto() throws Exception {
        String json = "{\"message\":\"Help\"}";
        
        TutorTurnResult result = new TutorTurnResult(10L, 20L, 20L, TutorMode.EXPLAIN, "Ans", true, Collections.emptyList());
        when(tutorTurnService.turn(any(TutorTurnCommand.class))).thenReturn(result);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());
                
        verify(tutorTurnService).turn(argThat(cmd -> cmd.requestedMode() == null));
    }

    @Test
    void c7_explicitConceptIdPassedUnchanged() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", 99L, null);
        TutorTurnResult result = new TutorTurnResult(10L, 99L, 99L, TutorMode.EXPLAIN, "Ans", true, Collections.emptyList());
        
        when(tutorTurnService.turn(any(TutorTurnCommand.class))).thenReturn(result);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
                
        verify(tutorTurnService).turn(argThat(cmd -> cmd.conceptId().equals(99L)));
    }

    @Test
    void c8_malformedRequestedMode_Returns400() throws Exception {
        String json = "{\"message\":\"Help\", \"requestedMode\":\"INVALID\"}";
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
                
        verify(tutorTurnService, never()).turn(any());
    }

    @Test
    void c9_blankMessage_Returns400() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("", null, null);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
                
        verify(tutorTurnService, never()).turn(any());
    }

    @Test
    void c10_malformedConversationId_Returns400() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", null, null);
        
        mockMvc.perform(post("/api/tutor/conversations/invalid/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void c11_clientSendsExtraUserId_Ignored() throws Exception {
        String json = "{\"message\":\"Help\", \"userId\":999}";
        TutorTurnResult result = new TutorTurnResult(10L, 20L, 20L, TutorMode.EXPLAIN, "Ans", true, Collections.emptyList());
        when(tutorTurnService.turn(any(TutorTurnCommand.class))).thenReturn(result);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());
                
        verify(tutorTurnService).turn(argThat(cmd -> cmd.userId().equals(1L)));
    }
    
    @Test
    void c12_responseEnumSerializationExact() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", null, null);
        TutorTurnResult result = new TutorTurnResult(10L, 20L, 20L, TutorMode.EXPLAIN, "Ans", true, Collections.emptyList());
        when(tutorTurnService.turn(any(TutorTurnCommand.class))).thenReturn(result);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("EXPLAIN"));
    }
    
    @Test
    void c13_nullTargetSerializeCorrectly() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", null, null);
        TutorTurnResult result = new TutorTurnResult(10L, null, null, TutorMode.EXPLAIN, "Ans", true, Collections.emptyList());
        when(tutorTurnService.turn(any(TutorTurnCommand.class))).thenReturn(result);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetConceptId").isEmpty())
                .andExpect(jsonPath("$.focusConceptId").isEmpty());
    }
    
    @Test
    void c14_citationsSerializedThroughContract() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", null, null);
        List<CitationDto> citations = Collections.singletonList(new CitationDto("S1", 1L, 1L, "Doc", "text", null, null));
        TutorTurnResult result = new TutorTurnResult(10L, null, null, TutorMode.EXPLAIN, "Ans", true, citations);
        when(tutorTurnService.turn(any(TutorTurnCommand.class))).thenReturn(result);
        
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].citationId").value("S1"))
                .andExpect(jsonPath("$.citations[0].documentId").value(1))
                .andExpect(jsonPath("$.citations[0].documentTitle").value("Doc"));
    }

    @Test
    void testMissingConversation_404() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", null, null);
        when(tutorTurnService.turn(any(TutorTurnCommand.class)))
            .thenThrow(new IllegalArgumentException("not found or unauthorized"));
            
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testNotebookless_409() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", null, null);
        when(tutorTurnService.turn(any(TutorTurnCommand.class)))
            .thenThrow(new IllegalArgumentException("non-notebook"));
            
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void testInvalidConcept_400() throws Exception {
        TutorMessageRequest request = new TutorMessageRequest("Help", 99L, null);
        when(tutorTurnService.turn(any(TutorTurnCommand.class)))
            .thenThrow(new IllegalArgumentException("Explicit concept not in active candidate map"));
            
        mockMvc.perform(post("/api/tutor/conversations/10/messages")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}