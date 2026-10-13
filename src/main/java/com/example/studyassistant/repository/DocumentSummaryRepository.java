package com.example.studyassistant.repository;

import com.example.studyassistant.entity.DocumentSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentSummaryRepository extends JpaRepository<DocumentSummary, Long> {

    Optional<DocumentSummary> findByDocumentIdAndIndexVersion(Long documentId, String indexVersion);
}
