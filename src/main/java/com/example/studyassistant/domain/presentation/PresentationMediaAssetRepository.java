package com.example.studyassistant.domain.presentation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PresentationMediaAssetRepository extends JpaRepository<PresentationMediaAsset, Long> {
    List<PresentationMediaAsset> findByDeckIdOrderByCreatedAtDescIdDesc(Long deckId);
    List<PresentationMediaAsset> findByDeckIdAndOwnerIdOrderByCreatedAtDescIdDesc(Long deckId, Long ownerId);
    Optional<PresentationMediaAsset> findByIdAndDeckIdAndOwnerId(Long id, Long deckId, Long ownerId);
    @Query("SELECT COALESCE(SUM(m.sizeBytes), 0) FROM PresentationMediaAsset m WHERE m.owner.id = :ownerId")
    long sumSizeBytesByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT m.storedFileKey FROM PresentationMediaAsset m")
    List<String> findAllStoredFileKeys();

    boolean existsByStoredFileKey(String storedFileKey);
}
