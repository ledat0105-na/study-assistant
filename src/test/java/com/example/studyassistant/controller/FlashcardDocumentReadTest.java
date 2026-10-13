package com.example.studyassistant.controller;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FlashcardDocumentReadTest {
    @Test void deletedCardsAreExcludedAndForeignDocumentIsDenied() {
        var cards = mock(FlashcardRepository.class); var docs = mock(DocumentRepository.class);
        var request = mock(HttpServletRequest.class); var session = mock(HttpSession.class);
        var controller = new FlashcardController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "flashcardRepository", cards);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "documentRepository", docs);
        when(request.getSession(false)).thenReturn(session); when(session.getAttribute("userId")).thenReturn(1L);
        when(docs.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(new Document()));
        Flashcard active = new Flashcard(), deleted = new Flashcard(); deleted.setDeletedAt(java.time.Instant.now());
        when(cards.findByDocumentId(10L)).thenReturn(List.of(deleted, active));
        assertEquals(List.of(active), controller.getFlashcardsForDocument(10L, request).getBody());
        when(docs.findByIdAndUserId(11L, 1L)).thenReturn(Optional.empty());
        assertEquals(404, controller.getFlashcardsForDocument(11L, request).getStatusCode().value());
        verify(cards, never()).findByDocumentId(11L);
    }
}
