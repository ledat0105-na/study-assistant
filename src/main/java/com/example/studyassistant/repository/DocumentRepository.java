package com.example.studyassistant.repository;

import com.example.studyassistant.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "notebook", "notebook.user"})
    java.util.Optional<Document> findKnowledgeGraphDocumentByIdAndUserId(Long id, Long userId);
    List<Document> findByUserId(Long userId);
    List<Document> findByUserIdAndNotebookId(Long userId, Long notebookId);
    Optional<Document> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT d FROM Document d WHERE d.id = :id")
    Optional<Document> findByIdWithPessimisticLock(@org.springframework.data.repository.query.Param("id") Long id);
}
