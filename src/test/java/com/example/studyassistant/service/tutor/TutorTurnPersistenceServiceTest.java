package com.example.studyassistant.service.tutor;

import com.example.studyassistant.entity.Conversation;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.entity.TutorSession;
import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.ChatMessage;
import com.example.studyassistant.repository.ConversationRepository;
import com.example.studyassistant.repository.TutorSessionRepository;
import com.example.studyassistant.repository.ConceptRepository;
import com.example.studyassistant.repository.ChatMessageRepository;
import com.example.studyassistant.retrieval.model.CitationDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TutorTurnPersistenceServiceTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private TutorSessionRepository tutorSessionRepository;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    
    private ObjectMapper objectMapper = new ObjectMapper();

    private TutorTurnPersistenceService service;

    private User user;
    private Notebook notebook;
    private Conversation conversation;
    private Concept concept;

    @BeforeEach
    void setUp() {
        service = new TutorTurnPersistenceService(entityManager, tutorSessionRepository, conceptRepository, chatMessageRepository);
        
        user = new User();
        user.setId(1L);

        notebook = new Notebook();
        notebook.setId(100L);

        conversation = new Conversation();
        conversation.setId(10L);
        conversation.setUser(user);
        conversation.setNotebook(notebook);
        conversation.setIsDeleted(false);
        
        concept = new Concept();
        concept.setId(42L);
        concept.setNotebook(notebook);
    }

    @Test
    void testPersistTurn_CreatesSessionLazilyAndSavesMessages() {
        when(entityManager.find(Conversation.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(conversation);
        when(tutorSessionRepository.findByConversationId(10L)).thenReturn(Optional.empty());
        
        service.persistTurn(1L, 10L, null, "User text", "Assistant text", Collections.emptyList());
        
        verify(tutorSessionRepository, times(1)).save(any(TutorSession.class));
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }
    
    @Test
    void testPersistTurn_UpdatesExistingSessionAndClearsConcept() {
        when(entityManager.find(Conversation.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(conversation);
        
        TutorSession session = new TutorSession();
        session.setCurrentConcept(concept);
        when(tutorSessionRepository.findByConversationId(10L)).thenReturn(Optional.of(session));
        
        service.persistTurn(1L, 10L, null, "U", "A", null);
        
        assertThat(session.getCurrentConcept()).isNull();
        verify(tutorSessionRepository, never()).save(session); // automatically managed in TX
    }
    
    @Test
    void testPersistTurn_UpdatesCurrentConcept() {
        when(entityManager.find(Conversation.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(conversation);
        when(tutorSessionRepository.findByConversationId(10L)).thenReturn(Optional.empty());
        when(conceptRepository.findById(42L)).thenReturn(Optional.of(concept));
        
        service.persistTurn(1L, 10L, 42L, "U", "A", null);
        
        verify(tutorSessionRepository, times(1)).save(argThat(s -> s.getCurrentConcept().getId().equals(42L)));
    }
    
    @Test
    void testPersistTurn_RejectsWrongNotebookConcept() {
        when(entityManager.find(Conversation.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(conversation);
        when(tutorSessionRepository.findByConversationId(10L)).thenReturn(Optional.empty());
        
        Notebook otherNb = new Notebook();
        otherNb.setId(999L);
        concept.setNotebook(otherNb);
        when(conceptRepository.findById(42L)).thenReturn(Optional.of(concept));
        
        assertThatThrownBy(() -> service.persistTurn(1L, 10L, 42L, "U", "A", null))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    void testPersistTurn_SerializesCitations() throws Exception {
        when(entityManager.find(Conversation.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(conversation);
        when(tutorSessionRepository.findByConversationId(10L)).thenReturn(Optional.empty());
        
        CitationDto citation = new CitationDto("S1", 1L, 1L, "Doc", "text", null, null);
        
        service.persistTurn(1L, 10L, null, "U", "A", Collections.singletonList(citation));
        
        verify(chatMessageRepository).save(argThat(m -> m.getSender().equals("ASSISTANT") && m.getSourcesJson() != null && m.getSourcesJson().contains("S1")));
    }
}