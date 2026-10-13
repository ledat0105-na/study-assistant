package com.example.studyassistant.repository;

import com.example.studyassistant.entity.DocumentSummarySource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentSummarySourceRepository extends JpaRepository<DocumentSummarySource, Long> {

    List<DocumentSummarySource> findBySummaryIdOrderBySourceOrderAsc(Long summaryId);

    void deleteBySummaryId(Long summaryId);
}
