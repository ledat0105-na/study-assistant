package com.example.studyassistant.domain.flashcard.generation;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.repository.DocumentChunkRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BoundedActiveChunkSelectorTest {
    @Test void samplesWholeDocumentIncludingEndWithoutMutatingInput() {
        var repo = mock(DocumentChunkRepository.class);
        List<DocumentChunk> original = new ArrayList<>();
        for (int i = 199; i >= 0; i--) { var c = new DocumentChunk(); c.setId((long) i); c.setChunkIndex(i); original.add(c); }
        when(repo.findByDocumentIdAndIndexVersion(10L, "v1")).thenReturn(List.copyOf(original));
        var selector = new BoundedActiveChunkSelector(repo);
        var result = selector.selectChunks(10L, "v1");
        assertEquals(60, result.size()); assertEquals(0, result.get(0).getChunkIndex());
        assertEquals(199, result.get(59).getChunkIndex()); assertEquals(199, original.get(0).getChunkIndex());
        assertEquals(60, result.stream().map(DocumentChunk::getId).distinct().count());
        assertEquals(result, selector.selectChunks(10L, "v1"));
    }
}
