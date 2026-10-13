package com.example.studyassistant.domain.quiz.generation;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuizActiveChunkSelectorTest {
    final DocumentRepository documents = mock(DocumentRepository.class);
    final DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
    final QuizActiveChunkSelector selector = new QuizActiveChunkSelector(documents, chunks,
            mock(ConceptSourceRepository.class), mock(TopicRepository.class));
    Document doc(long id) {
        Document doc = new Document(); doc.setId(id); doc.setStatus("READY"); doc.setActiveIndexVersion("v1"); return doc;
    }
    List<DocumentChunk> chunks(Document doc, int count) {
        List<DocumentChunk> list = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            DocumentChunk c = new DocumentChunk(); c.setId(doc.getId() * 1000 + i); c.setDocument(doc); c.setChunkIndex(i);
            c.setIndexVersion("v1"); list.add(c);
        }
        when(chunks.findByDocumentIdAndIndexVersion(doc.getId(), "v1")).thenReturn(list);
        return list;
    }
    GroundedQuizGenerationCommand command(QuizScopeType scope) {
        return new GroundedQuizGenerationCommand(1L, scope, scope == QuizScopeType.NOTEBOOK ? 2L : null,
                scope == QuizScopeType.DOCUMENT ? 10L : null, null, null, QuizMode.PRACTICE, null, null, null, null, null);
    }
    @Test void evenlySamplesWholeDocumentIncludingLastChunkWithoutMutatingRepositoryList() {
        Document doc = doc(10); when(documents.findById(10L)).thenReturn(Optional.of(doc));
        List<DocumentChunk> original = chunks(doc, 200);
        var first = selector.selectEvidence(command(QuizScopeType.DOCUMENT));
        var second = selector.selectEvidence(command(QuizScopeType.DOCUMENT));
        assertEquals(60, first.size());
        assertEquals(0, first.get(0).getChunkIndex()); assertEquals(199, first.get(59).getChunkIndex());
        assertEquals(first.stream().map(DocumentChunk::getId).toList(), second.stream().map(DocumentChunk::getId).toList());
        assertEquals(60, first.stream().map(DocumentChunk::getId).distinct().count());
        assertEquals(199, original.get(0).getChunkIndex());
    }
    @Test void notebookDoesNotStopBeforeReadingLaterDocuments() {
        Document first = doc(10), last = doc(11), failed = doc(12); failed.setStatus("FAILED");
        when(documents.findByUserIdAndNotebookId(1L, 2L)).thenReturn(List.of(last, failed, first));
        chunks(first, 100); chunks(last, 100);
        var result = selector.selectEvidence(command(QuizScopeType.NOTEBOOK));
        assertEquals(60, result.size());
        assertEquals(10L, result.get(0).getDocument().getId());
        assertEquals(11L, result.get(59).getDocument().getId());
        verify(chunks, never()).findByDocumentIdAndIndexVersion(12L, "v1");
    }
    @Test void shortDocumentIsReturnedInChunkOrderAndInactiveDocumentHasNoEvidence() {
        Document doc = doc(10); when(documents.findById(10L)).thenReturn(Optional.of(doc)); chunks(doc, 3);
        assertEquals(List.of(0, 1, 2), selector.selectEvidence(command(QuizScopeType.DOCUMENT)).stream().map(DocumentChunk::getChunkIndex).toList());
        doc.setStatus("DELETED"); assertTrue(selector.selectEvidence(command(QuizScopeType.DOCUMENT)).isEmpty());
    }
}
