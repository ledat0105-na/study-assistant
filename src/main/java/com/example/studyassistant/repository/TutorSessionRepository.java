package com.example.studyassistant.repository;

import com.example.studyassistant.entity.TutorSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TutorSessionRepository extends JpaRepository<TutorSession, Long> {
    Optional<TutorSession> findByConversationId(Long conversationId);
}
