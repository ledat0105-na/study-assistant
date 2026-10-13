package com.example.studyassistant.service;

import com.example.studyassistant.dto.TutorSessionState;
import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.Conversation;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.TutorSession;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.ConversationRepository;
import com.example.studyassistant.repository.TutorSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TutorSessionServiceTest {

    @Mock
    private TutorSessionRepository tutorSessionRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private TutorSessionService tutorSessionService;

    private User user;
    private Notebook notebook;
    private Conversation conversation;
    private TutorSession tutorSession;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);

        notebook = new Notebook();
        notebook.setId(10L);

        conversation = new Conversation();
        conversation.setId(100L);
        conversation.setUser(user);
        conversation.setNotebook(notebook);
        conversation.setIsDeleted(false);

        tutorSession = new TutorSession();
        tutorSession.setId(1000L);
        tutorSession.setConversation(conversation);
        tutorSession.setCreatedAt(LocalDateTime.now());
        tutorSession.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void s1_createTutorSessionLazily() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        
        when(tutorSessionRepository.findByConversationId(100L))
            .thenReturn(Optional.empty()) 
            .thenReturn(Optional.empty());
            
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(conversation));
        when(tutorSessionRepository.saveAndFlush(any(TutorSession.class))).thenReturn(tutorSession);

        TutorSessionState result = tutorSessionService.getOrCreateSession(1L, 100L);

        assertNotNull(result);
        assertEquals(100L, result.conversationId());
        assertEquals(1000L, result.tutorSessionId());
        verify(tutorSessionRepository, times(1)).saveAndFlush(any(TutorSession.class));
    }

    @Test
    void s2_secondGetReturnsSameSession() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(tutorSessionRepository.findByConversationId(100L)).thenReturn(Optional.of(tutorSession));

        TutorSessionState result = tutorSessionService.getOrCreateSession(1L, 100L);

        assertNotNull(result);
        assertEquals(1000L, result.tutorSessionId());
        verify(tutorSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void s3_s12_uniqueConversationInvariantRaceCondition() {
        when(transactionTemplate.execute(any()))
            .thenAnswer(invocation -> Optional.empty()) // Fast path read tx returns empty
            .thenThrow(new DataIntegrityViolationException("Duplicate")) // Create tx fails
            .thenAnswer(invocation -> {
                TransactionCallback<?> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            }); // Recovery read tx succeeds

        when(tutorSessionRepository.findByConversationId(100L)).thenReturn(Optional.of(tutorSession));

        TutorSessionState result = tutorSessionService.getOrCreateSession(1L, 100L);

        assertNotNull(result);
        assertEquals(1000L, result.tutorSessionId());
    }

    @Test
    void s4_currentConceptNullable() {
        assertNull(tutorSession.getCurrentConcept());
    }

    @Test
    void s5_s9_updateCurrentConcept() {
        Concept concept = new Concept();
        concept.setId(50L);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        
        when(tutorSessionRepository.findByConversationId(100L)).thenReturn(Optional.of(tutorSession));
        when(tutorSessionRepository.save(any(TutorSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TutorSessionState updated = tutorSessionService.updateCurrentConcept(1L, 100L, concept);

        assertNotNull(updated);
        assertEquals(50L, updated.currentConceptId());
        verify(tutorSessionRepository, times(1)).save(tutorSession);
    }

    @Test
    void s6_conversationOwnershipValidation() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(tutorSessionRepository.findByConversationId(100L)).thenReturn(Optional.of(tutorSession));

        assertThrows(IllegalArgumentException.class, () -> {
            tutorSessionService.getOrCreateSession(2L, 100L);
        });
    }

    @Test
    void s7_genericConversationWithoutNotebookFails() {
        conversation.setNotebook(null);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(tutorSessionRepository.findByConversationId(100L)).thenReturn(Optional.of(tutorSession));

        assertThrows(IllegalArgumentException.class, () -> {
            tutorSessionService.getOrCreateSession(1L, 100L);
        });
    }
    
    @Test
    void s14_detachedBoundaryTest() {
        TutorSessionState state = new TutorSessionState(1000L, 100L, 50L);
        assertEquals(1000L, state.tutorSessionId());
        assertEquals(100L, state.conversationId());
        assertEquals(50L, state.currentConceptId());
    }
}
