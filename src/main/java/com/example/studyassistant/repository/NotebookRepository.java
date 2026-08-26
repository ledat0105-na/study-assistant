package com.example.studyassistant.repository;

import com.example.studyassistant.entity.Notebook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotebookRepository extends JpaRepository<Notebook, Long> {
    List<Notebook> findByUserIdAndIsDeletedFalse(Long userId);
    Optional<Notebook> findByIdAndUserIdAndIsDeletedFalse(Long id, Long userId);
    long countByUserIdAndIsDeletedFalse(Long userId);
}
