package com.example.studyassistant.service;

import com.example.studyassistant.dto.TutorSessionState;
import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.Conversation;
import com.example.studyassistant.entity.TutorSession;
import com.example.studyassistant.repository.ConversationRepository;
import com.example.studyassistant.repository.TutorSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TutorSessionService {

    @Autowired
    private TutorSessionRepository tutorSessionRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public TutorSessionState getOrCreateSession(Long userId, Long conversationId) {
        Optional<TutorSessionState> state = transactionTemplate.execute(status -> {
            Optional<TutorSession> existing = tutorSessionRepository.findByConversationId(conversationId);
            if (existing.isPresent()) {
                TutorSession session = existing.get();
                validateConversation(session.getConversation(), userId);
                return Optional.of(mapToState(session));
            }
            return Optional.empty();
        });

        if (state.isPresent()) {
            return state.get();
        }

        try {
            return transactionTemplate.execute(status -> {
                Conversation conversation = conversationRepository.findById(conversationId)
                        .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

                validateConversation(conversation, userId);

                Optional<TutorSession> insideTx = tutorSessionRepository.findByConversationId(conversationId);
                if (insideTx.isPresent()) {
                    return mapToState(insideTx.get());
                }

                TutorSession session = new TutorSession();
                session.setConversation(conversation);
                session.setCreatedAt(LocalDateTime.now());
                session.setUpdatedAt(LocalDateTime.now());
                session = tutorSessionRepository.saveAndFlush(session);
                return mapToState(session);
            });
        } catch (DataIntegrityViolationException e) {
            return transactionTemplate.execute(status -> {
                TutorSession session = tutorSessionRepository.findByConversationId(conversationId)
                        .orElseThrow(() -> new IllegalStateException("Failed to load TutorSession after race condition", e));
                validateConversation(session.getConversation(), userId);
                return mapToState(session);
            });
        }
    }

    public TutorSessionState updateCurrentConcept(Long userId, Long conversationId, Concept targetConcept) {
        return transactionTemplate.execute(status -> {
            TutorSession session = tutorSessionRepository.findByConversationId(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("TutorSession not found"));
            
            validateConversation(session.getConversation(), userId);
            
            session.setCurrentConcept(targetConcept);
            session.setUpdatedAt(LocalDateTime.now());
            session = tutorSessionRepository.save(session);
            return mapToState(session);
        });
    }

    private void validateConversation(Conversation conversation, Long userId) {
        if (conversation.getIsDeleted()) {
            throw new IllegalArgumentException("Conversation is deleted");
        }
        if (!conversation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("B\u1ea1n kh\u00f4ng c\u00f3 quy\u1ec1n truy c\u1eadp (Ownership violation)");
        }
        if (conversation.getNotebook() == null) {
            throw new IllegalArgumentException("Conversation does not have a notebook. Not valid for notebook-grounded Tutor V1.");
        }
    }

    private TutorSessionState mapToState(TutorSession session) {
        Long currentConceptId = null;
        if (session.getCurrentConcept() != null) {
            currentConceptId = session.getCurrentConcept().getId();
        }
        return new TutorSessionState(
            session.getId(),
            session.getConversation().getId(),
            currentConceptId
        );
    }
}
