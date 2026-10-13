package com.example.studyassistant.service.tutor;

import com.example.studyassistant.entity.Conversation;
import com.example.studyassistant.entity.TutorSession;
import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.ChatMessage;
import com.example.studyassistant.repository.ConversationRepository;
import com.example.studyassistant.repository.TutorSessionRepository;
import com.example.studyassistant.repository.ConceptRepository;
import com.example.studyassistant.repository.ChatMessageRepository;
import com.example.studyassistant.retrieval.model.CitationDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TutorTurnPersistenceService {

    private final EntityManager entityManager;
    private final TutorSessionRepository tutorSessionRepository;
    private final ConceptRepository conceptRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TutorTurnPersistenceService(
            EntityManager entityManager,
            TutorSessionRepository tutorSessionRepository,
            ConceptRepository conceptRepository,
            ChatMessageRepository chatMessageRepository) {
        this.entityManager = entityManager;
        this.tutorSessionRepository = tutorSessionRepository;
        this.conceptRepository = conceptRepository;
        this.chatMessageRepository = chatMessageRepository;
        
    }

    @Transactional
    public void persistTurn(
            Long userId,
            Long conversationId,
            Long resolvedTargetConceptId,
            String userMessage,
            String assistantMessage,
            List<CitationDto> citations) {
        
        Conversation conv = entityManager.find(Conversation.class, conversationId, LockModeType.PESSIMISTIC_WRITE);
        
        if (conv == null || !conv.getUser().getId().equals(userId) || conv.getIsDeleted() || conv.getNotebook() == null) {
            throw new IllegalArgumentException("Conversation invalid, deleted, unauthorized, or non-notebook");
        }
        
        TutorSession session = tutorSessionRepository.findByConversationId(conversationId).orElse(null);
        if (session == null) {
            session = new TutorSession();
            session.setConversation(conv);
            tutorSessionRepository.save(session);
        }
        
        if (resolvedTargetConceptId != null) {
            Concept concept = conceptRepository.findById(resolvedTargetConceptId)
                .orElseThrow(() -> new IllegalArgumentException("Concept not found"));
            if (!concept.getNotebook().getId().equals(conv.getNotebook().getId())) {
                throw new IllegalArgumentException("Concept does not belong to the same notebook");
            }
            session.setCurrentConcept(concept);
        } else {
            session.setCurrentConcept(null);
        }
        
        ChatMessage uMsg = new ChatMessage();
        uMsg.setConversation(conv);
        uMsg.setSender("USER");
        uMsg.setContent(userMessage);
        
        ChatMessage aMsg = new ChatMessage();
        aMsg.setConversation(conv);
        aMsg.setSender("ASSISTANT");
        aMsg.setContent(assistantMessage);
        
        if (citations != null && !citations.isEmpty()) {
            try {
                aMsg.setSourcesJson(objectMapper.writeValueAsString(citations));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize citations", e);
            }
        }
        
        chatMessageRepository.save(uMsg);
        chatMessageRepository.save(aMsg);
        
        conv.setUpdatedAt(LocalDateTime.now());
    }
}