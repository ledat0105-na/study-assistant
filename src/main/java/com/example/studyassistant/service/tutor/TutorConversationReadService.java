package com.example.studyassistant.service.tutor;

import com.example.studyassistant.dto.TutorSessionState;
import com.example.studyassistant.entity.Conversation;
import com.example.studyassistant.entity.TutorSession;
import com.example.studyassistant.entity.ChatMessage;
import com.example.studyassistant.repository.ConversationRepository;
import com.example.studyassistant.repository.TutorSessionRepository;
import com.example.studyassistant.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TutorConversationReadService {
    private final ConversationRepository conversationRepository;
    private final TutorSessionRepository tutorSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public TutorConversationReadService(
            ConversationRepository conversationRepository,
            TutorSessionRepository tutorSessionRepository,
            ChatMessageRepository chatMessageRepository) {
        this.conversationRepository = conversationRepository;
        this.tutorSessionRepository = tutorSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional(readOnly = true)
    public TutorConversationState findConversationState(Long userId, Long conversationId) {
        Conversation conv = conversationRepository.findByIdAndUserIdAndIsDeletedFalse(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found or unauthorized"));
        
        if (conv.getNotebook() == null) {
            throw new IllegalArgumentException("Tutor V1 does not support non-notebook conversations");
        }
        
        return new TutorConversationState(conv.getId(), conv.getNotebook().getId());
    }

    @Transactional(readOnly = true)
    public Optional<TutorSessionState> findSessionState(Long userId, Long conversationId) {
        // Validate ownership first
        conversationRepository.findByIdAndUserIdAndIsDeletedFalse(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found or unauthorized"));
                
        return tutorSessionRepository.findByConversationId(conversationId)
                .map(session -> new TutorSessionState(
                    session.getId(),
                    session.getConversation().getId(),
                    session.getCurrentConcept() != null ? session.getCurrentConcept().getId() : null
                ));
    }
    
    @Transactional(readOnly = true)
    public List<TutorHistoryMessage> findRecentHistory(Long conversationId) {
        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        int skip = Math.max(0, messages.size() - 6);
        return messages.stream()
                .skip(skip)
                .map(m -> new TutorHistoryMessage(m.getSender(), m.getContent()))
                .collect(Collectors.toList());
    }
}
