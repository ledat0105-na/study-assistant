package com.example.studyassistant.service.tutor;

import com.example.studyassistant.entity.Conversation;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.dto.TutorSessionState;
import com.example.studyassistant.entity.TutorSession;
import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.ChatMessage;
import com.example.studyassistant.repository.ConversationRepository;
import com.example.studyassistant.repository.TutorSessionRepository;
import com.example.studyassistant.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TutorConversationReadServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private TutorSessionRepository tutorSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private TutorConversationReadService service;

    private User user;
    private Notebook notebook;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);

        notebook = new Notebook();
        notebook.setId(100L);

        conversation = new Conversation();
        conversation.setId(10L);
        conversation.setUser(user);
        conversation.setNotebook(notebook);
        conversation.setIsDeleted(false);
    }

    @Test
    void testFindConversationState_Success() {
        when(conversationRepository.findByIdAndUserIdAndIsDeletedFalse(10L, 1L))
                .thenReturn(Optional.of(conversation));

        TutorConversationState state = service.findConversationState(1L, 10L);
        assertThat(state.conversationId()).isEqualTo(10L);
        assertThat(state.notebookId()).isEqualTo(100L);
    }
    
    @Test
    void testFindConversationState_NoNotebook() {
        conversation.setNotebook(null);
        when(conversationRepository.findByIdAndUserIdAndIsDeletedFalse(10L, 1L))
                .thenReturn(Optional.of(conversation));
                
        assertThatThrownBy(() -> service.findConversationState(1L, 10L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFindSessionState_MissingReturnsEmpty() {
        when(conversationRepository.findByIdAndUserIdAndIsDeletedFalse(10L, 1L))
                .thenReturn(Optional.of(conversation));
        when(tutorSessionRepository.findByConversationId(10L)).thenReturn(Optional.empty());
        
        Optional<TutorSessionState> res = service.findSessionState(1L, 10L);
        assertThat(res).isEmpty();
        verify(tutorSessionRepository, times(1)).findByConversationId(10L);
    }

    @Test
    void testFindSessionState_ExistingReturnsImmutableState() {
        when(conversationRepository.findByIdAndUserIdAndIsDeletedFalse(10L, 1L))
                .thenReturn(Optional.of(conversation));
                
        Concept c = new Concept();
        c.setId(42L);
        TutorSession session = new TutorSession();
        session.setId(5L);
        session.setConversation(conversation);
        session.setCurrentConcept(c);
        
        when(tutorSessionRepository.findByConversationId(10L)).thenReturn(Optional.of(session));
        
        TutorSessionState state = service.findSessionState(1L, 10L).get();
        assertThat(state.tutorSessionId()).isEqualTo(5L);
        assertThat(state.conversationId()).isEqualTo(10L);
        assertThat(state.currentConceptId()).isEqualTo(42L);
    }
    
    @Test
    void testFindRecentHistory_Max6Chronological() {
        List<ChatMessage> msgs = Arrays.asList(
            new ChatMessage(1L, conversation, "USER", "1", null, null),
            new ChatMessage(2L, conversation, "ASSISTANT", "2", null, null),
            new ChatMessage(3L, conversation, "USER", "3", null, null),
            new ChatMessage(4L, conversation, "ASSISTANT", "4", null, null),
            new ChatMessage(5L, conversation, "USER", "5", null, null),
            new ChatMessage(6L, conversation, "ASSISTANT", "6", null, null),
            new ChatMessage(7L, conversation, "USER", "7", null, null),
            new ChatMessage(8L, conversation, "ASSISTANT", "8", null, null)
        );
        
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(10L)).thenReturn(msgs);
        
        List<TutorHistoryMessage> result = service.findRecentHistory(10L);
        assertThat(result).hasSize(6);
        assertThat(result.get(0).content()).isEqualTo("3");
        assertThat(result.get(5).content()).isEqualTo("8");
    }
}
