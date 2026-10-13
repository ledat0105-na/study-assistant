package com.example.studyassistant.service;

import com.example.studyassistant.dto.ConceptMasteryDetailDto;
import com.example.studyassistant.dto.ConceptMasteryItemDto;
import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.exception.MasteryConceptNotFoundException;
import com.example.studyassistant.repository.StudentMasteryReadRepository;
import com.example.studyassistant.repository.projection.GroundedConceptMasteryDetailProjection;
import com.example.studyassistant.repository.projection.GroundedConceptMasteryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import com.example.studyassistant.dto.MasteryPageDto;

@Service
@RequiredArgsConstructor
public class StudentMasteryReadService {

    private final StudentMasteryReadRepository repository;

    @Transactional(readOnly = true)
    public MasteryPageDto<ConceptMasteryItemDto> getNotebookMastery(Long userId, Long notebookId, Pageable pageable) {
        Slice<GroundedConceptMasteryProjection> slice = repository.findGroundedConceptMasteriesByNotebookId(userId, notebookId, pageable);
        
        List<ConceptMasteryItemDto> items = slice.getContent().stream()
                .map(this::mapToItemDto)
                .collect(Collectors.toList());
                
        return MasteryPageDto.<ConceptMasteryItemDto>builder()
                .items(items)
                .page(slice.getNumber())
                .size(slice.getSize())
                .hasNext(slice.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public ConceptMasteryDetailDto getConceptMastery(Long userId, Long notebookId, Long conceptId) {
        return repository.findGroundedConceptMasteryDetail(userId, notebookId, conceptId)
                .map(this::mapToDetailDto)
                .orElseThrow(() -> new MasteryConceptNotFoundException("Concept mastery not found or not accessible"));
    }

    private ConceptMasteryItemDto mapToItemDto(GroundedConceptMasteryProjection projection) {
        if (projection.getMasteryId() == null) {
            return ConceptMasteryItemDto.builder()
                    .conceptId(projection.getConceptId())
                    .canonicalName(projection.getCanonicalName())
                    .conceptType(projection.getConceptType())
                    .state(MasteryState.UNSEEN)
                    .masteryEstimate(null)
                    .evidenceCount(0)
                    .lastEvidenceAt(null)
                    .build();
        }

        validateObservedProjection(projection.getMasteryEstimate(), projection.getEvidenceCount());

        return ConceptMasteryItemDto.builder()
                .conceptId(projection.getConceptId())
                .canonicalName(projection.getCanonicalName())
                .conceptType(projection.getConceptType())
                .state(MasteryState.OBSERVED)
                .masteryEstimate(projection.getMasteryEstimate())
                .evidenceCount(projection.getEvidenceCount())
                .lastEvidenceAt(projection.getLastEvidenceAt())
                .build();
    }

    private ConceptMasteryDetailDto mapToDetailDto(GroundedConceptMasteryDetailProjection projection) {
        if (projection.getMasteryId() == null) {
            return ConceptMasteryDetailDto.builder()
                    .conceptId(projection.getConceptId())
                    .canonicalName(projection.getCanonicalName())
                    .conceptType(projection.getConceptType())
                    .description(projection.getDescription())
                    .state(MasteryState.UNSEEN)
                    .masteryEstimate(null)
                    .evidenceCount(0)
                    .lastEvidenceAt(null)
                    .build();
        }

        validateObservedProjection(projection.getMasteryEstimate(), projection.getEvidenceCount());

        return ConceptMasteryDetailDto.builder()
                .conceptId(projection.getConceptId())
                .canonicalName(projection.getCanonicalName())
                .conceptType(projection.getConceptType())
                .description(projection.getDescription())
                .state(MasteryState.OBSERVED)
                .masteryEstimate(projection.getMasteryEstimate())
                .evidenceCount(projection.getEvidenceCount())
                .lastEvidenceAt(projection.getLastEvidenceAt())
                .build();
    }

    private void validateObservedProjection(Double masteryEstimate, Integer evidenceCount) {
        if (masteryEstimate == null || evidenceCount == null) {
            throw new IllegalStateException("Corrupted mastery row: masteryEstimate or evidenceCount is null");
        }
    }
}
