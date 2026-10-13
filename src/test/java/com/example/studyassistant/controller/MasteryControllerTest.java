package com.example.studyassistant.controller;

import com.example.studyassistant.dto.ConceptMasteryDetailDto;
import com.example.studyassistant.dto.ConceptMasteryItemDto;
import com.example.studyassistant.dto.MasteryPageDto;
import com.example.studyassistant.dto.MasteryState;
import com.example.studyassistant.exception.MasteryConceptNotFoundException;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.service.StudentMasteryReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasteryControllerTest {

    @Mock
    private StudentMasteryReadService masteryReadService;

    @Mock
    private NotebookRepository notebookRepository;

    @InjectMocks
    private MasteryController controller;

    private MockHttpServletRequest request;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        session = new MockHttpSession();
        session.setAttribute("userId", 1L);
        request.setSession(session);
    }

    private void unauthenticate() {
        request.setSession(new MockHttpSession());
    }

    @Test
    void testCaseA_UnauthenticatedList() {
        unauthenticate();
        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 50, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testCaseB_UnauthenticatedDetail() {
        unauthenticate();
        ResponseEntity<?> response = controller.getConceptMastery(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testCaseC_OwnedNotebookEmptyList() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(true);
        MasteryPageDto<ConceptMasteryItemDto> pageDto = new MasteryPageDto<>(Collections.emptyList(), 0, 50, false);
        when(masteryReadService.getNotebookMastery(eq(1L), eq(10L), any())).thenReturn(pageDto);

        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 50, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        MasteryPageDto<?> body = (MasteryPageDto<?>) response.getBody();
        assertThat(body.getItems()).isEmpty();
    }

    @Test
    void testCaseD_MissingNotebookList() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(false);

        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 50, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testCaseE_ForeignNotebookList() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(false);

        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 50, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testCaseF_ListDefaultPage() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(true);
        MasteryPageDto<ConceptMasteryItemDto> pageDto = new MasteryPageDto<>(Collections.emptyList(), 0, 50, false);
        when(masteryReadService.getNotebookMastery(eq(1L), eq(10L), eq(PageRequest.of(0, 50)))).thenReturn(pageDto);

        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 50, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        MasteryPageDto<?> body = (MasteryPageDto<?>) response.getBody();
        assertThat(body.getPage()).isEqualTo(0);
        assertThat(body.getSize()).isEqualTo(50);
    }

    @Test
    void testCaseG_ListExplicitValidPageSize() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(true);
        MasteryPageDto<ConceptMasteryItemDto> pageDto = new MasteryPageDto<>(Collections.emptyList(), 2, 20, false);
        when(masteryReadService.getNotebookMastery(eq(1L), eq(10L), eq(PageRequest.of(2, 20)))).thenReturn(pageDto);

        ResponseEntity<?> response = controller.getNotebookMastery(10L, 2, 20, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        MasteryPageDto<?> body = (MasteryPageDto<?>) response.getBody();
        assertThat(body.getPage()).isEqualTo(2);
        assertThat(body.getSize()).isEqualTo(20);
    }

    @Test
    void testCaseH_PageLessThanZero() {
        ResponseEntity<?> response = controller.getNotebookMastery(10L, -1, 50, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testCaseI_SizeZero() {
        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 0, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testCaseJ_SizeGreaterThan100() {
        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 101, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testCaseK_UnseenSerialization() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(true);
        ConceptMasteryItemDto dto = ConceptMasteryItemDto.builder()
                .conceptId(1L)
                .canonicalName("A")
                .conceptType("C")
                .state(MasteryState.UNSEEN)
                .masteryEstimate(null)
                .evidenceCount(0)
                .lastEvidenceAt(null)
                .build();
        MasteryPageDto<ConceptMasteryItemDto> pageDto = new MasteryPageDto<>(List.of(dto), 0, 50, false);
        when(masteryReadService.getNotebookMastery(any(), any(), any())).thenReturn(pageDto);

        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 50, request);
        MasteryPageDto<ConceptMasteryItemDto> body = (MasteryPageDto<ConceptMasteryItemDto>) response.getBody();
        ConceptMasteryItemDto item = body.getItems().get(0);
        assertThat(item.getState()).isEqualTo(MasteryState.UNSEEN);
        assertThat(item.getMasteryEstimate()).isNull();
        assertThat(item.getEvidenceCount()).isEqualTo(0);
        assertThat(item.getLastEvidenceAt()).isNull();
    }

    @Test
    void testCaseL_ObservedSerialization() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(true);
        ConceptMasteryItemDto dto = ConceptMasteryItemDto.builder()
                .conceptId(1L)
                .canonicalName("A")
                .conceptType("C")
                .state(MasteryState.OBSERVED)
                .masteryEstimate(0.8)
                .evidenceCount(5)
                .lastEvidenceAt(null)
                .build();
        MasteryPageDto<ConceptMasteryItemDto> pageDto = new MasteryPageDto<>(List.of(dto), 0, 50, false);
        when(masteryReadService.getNotebookMastery(any(), any(), any())).thenReturn(pageDto);

        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 50, request);
        MasteryPageDto<ConceptMasteryItemDto> body = (MasteryPageDto<ConceptMasteryItemDto>) response.getBody();
        ConceptMasteryItemDto item = body.getItems().get(0);
        assertThat(item.getState()).isEqualTo(MasteryState.OBSERVED);
        assertThat(item.getMasteryEstimate()).isEqualTo(0.8);
        assertThat(item.getEvidenceCount()).isEqualTo(5);
    }

    @Test
    void testCaseM_ObservedZeroEvidence() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(true);
        ConceptMasteryItemDto dto = ConceptMasteryItemDto.builder()
                .conceptId(1L)
                .canonicalName("A")
                .conceptType("C")
                .state(MasteryState.OBSERVED)
                .masteryEstimate(0.0)
                .evidenceCount(0)
                .lastEvidenceAt(null)
                .build();
        MasteryPageDto<ConceptMasteryItemDto> pageDto = new MasteryPageDto<>(List.of(dto), 0, 50, false);
        when(masteryReadService.getNotebookMastery(any(), any(), any())).thenReturn(pageDto);

        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 50, request);
        MasteryPageDto<ConceptMasteryItemDto> body = (MasteryPageDto<ConceptMasteryItemDto>) response.getBody();
        ConceptMasteryItemDto item = body.getItems().get(0);
        assertThat(item.getState()).isEqualTo(MasteryState.OBSERVED);
        assertThat(item.getMasteryEstimate()).isEqualTo(0.0);
        assertThat(item.getEvidenceCount()).isEqualTo(0);
    }

    @Test
    void testCaseN_HasNextTrue() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(true);
        MasteryPageDto<ConceptMasteryItemDto> pageDto = new MasteryPageDto<>(Collections.emptyList(), 0, 50, true);
        when(masteryReadService.getNotebookMastery(any(), any(), any())).thenReturn(pageDto);

        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 50, request);
        MasteryPageDto<?> body = (MasteryPageDto<?>) response.getBody();
        assertThat(body.isHasNext()).isTrue();
    }

    @Test
    void testCaseO_HasNextFalse() {
        when(notebookRepository.existsByIdAndUserIdAndIsDeletedFalse(10L, 1L)).thenReturn(true);
        MasteryPageDto<ConceptMasteryItemDto> pageDto = new MasteryPageDto<>(Collections.emptyList(), 0, 50, false);
        when(masteryReadService.getNotebookMastery(any(), any(), any())).thenReturn(pageDto);

        ResponseEntity<?> response = controller.getNotebookMastery(10L, 0, 50, request);
        MasteryPageDto<?> body = (MasteryPageDto<?>) response.getBody();
        assertThat(body.isHasNext()).isFalse();
    }

    @Test
    void testCaseP_DetailValidUnseen() {
        ConceptMasteryDetailDto dto = ConceptMasteryDetailDto.builder()
                .conceptId(1L)
                .canonicalName("A")
                .conceptType("C")
                .description("Desc")
                .state(MasteryState.UNSEEN)
                .masteryEstimate(null)
                .evidenceCount(0)
                .lastEvidenceAt(null)
                .build();
        when(masteryReadService.getConceptMastery(1L, 10L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getConceptMastery(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ConceptMasteryDetailDto body = (ConceptMasteryDetailDto) response.getBody();
        assertThat(body.getState()).isEqualTo(MasteryState.UNSEEN);
    }

    @Test
    void testCaseQ_DetailValidObserved() {
        ConceptMasteryDetailDto dto = ConceptMasteryDetailDto.builder()
                .conceptId(1L)
                .state(MasteryState.OBSERVED)
                .build();
        when(masteryReadService.getConceptMastery(1L, 10L, 1L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getConceptMastery(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ConceptMasteryDetailDto body = (ConceptMasteryDetailDto) response.getBody();
        assertThat(body.getState()).isEqualTo(MasteryState.OBSERVED);
    }

    @Test
    void testCaseR_DetailMaskedNotFound() {
        when(masteryReadService.getConceptMastery(1L, 10L, 1L)).thenThrow(new MasteryConceptNotFoundException("Not found"));

        ResponseEntity<?> response = controller.getConceptMastery(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void testCaseS_DetailForeignStaleIdenticalMask() {
        when(masteryReadService.getConceptMastery(1L, 10L, 1L)).thenThrow(new MasteryConceptNotFoundException("Stale or foreign"));

        ResponseEntity<?> response = controller.getConceptMastery(10L, 1L, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }
}
