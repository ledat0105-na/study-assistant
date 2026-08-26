package com.example.studyassistant.repository;

import com.example.studyassistant.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Page<Conversation> findByUserIdAndIsDeletedFalse(Long userId, Pageable pageable);
    Optional<Conversation> findByIdAndUserIdAndIsDeletedFalse(Long id, Long userId);
    long countByUserIdAndIsDeletedFalse(Long userId);

    @Query("SELECT c FROM Conversation c WHERE c.user.id = :userId AND c.isDeleted = false AND LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Conversation> searchByUserIdAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);
}
