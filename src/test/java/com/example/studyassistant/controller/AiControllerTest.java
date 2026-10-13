package com.example.studyassistant.controller;

import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.TopicRepository;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.retrieval.service.GroundedAnswerService;
import com.example.studyassistant.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class AiControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private AiService aiService;

    @Mock
    private GroundedAnswerService groundedAnswerService;

    @InjectMocks
    private AiController aiController;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        request.getSession().setAttribute("userId", 1L);
    }

    @Test
    void testOversizedQuestionReturns400() {
        User mockUser = new User();
        mockUser.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        com.example.studyassistant.entity.Document mockDoc = new com.example.studyassistant.entity.Document();
        com.example.studyassistant.entity.Notebook mockNotebook = new com.example.studyassistant.entity.Notebook();
        mockNotebook.setId(1L);
        mockDoc.setNotebook(mockNotebook);
        
        when(documentRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(mockDoc));

        when(groundedAnswerService.answer(anyLong(), anyLong(), anyString()))
                .thenThrow(new IllegalArgumentException("Question exceeds available context limit"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", "A very long question");
        payload.put("docIds", java.util.Collections.singletonList(1L));

        ResponseEntity<?> response = aiController.chat(payload, request);
        
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().toString()).contains("Question exceeds available context limit");
    }

    @Test
    void testUnexpectedRuntimeExceptionReturns500() {
        User mockUser = new User();
        mockUser.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        com.example.studyassistant.entity.Document mockDoc = new com.example.studyassistant.entity.Document();
        com.example.studyassistant.entity.Notebook mockNotebook = new com.example.studyassistant.entity.Notebook();
        mockNotebook.setId(1L);
        mockDoc.setNotebook(mockNotebook);
        
        when(documentRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(mockDoc));

        when(groundedAnswerService.answer(anyLong(), anyLong(), anyString()))
                .thenThrow(new RuntimeException("DB offline"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", "A valid question");
        payload.put("docIds", java.util.Collections.singletonList(1L));

        ResponseEntity<?> response = aiController.chat(payload, request);
        
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().toString()).contains("DB offline");
    }
}
