package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.ConceptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConceptIntegrityClassificationTest {
    @Mock ConceptRepository repository;
    @Mock ConceptPersistenceHelper helper;
    ConceptService service() { return new ConceptService(repository, new ConceptNameNormalizer(), helper); }
    DocumentChunk chunk() {
        User owner = new User(); owner.setId(1L);
        Document doc = new Document(); doc.setId(10L); doc.setUser(owner); doc.setStatus("READY"); doc.setActiveIndexVersion("v1");
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(11L); chunk.setDocument(doc); chunk.setIndexVersion("v1"); return chunk;
    }
    @Test void duplicateRaceRecoversDocumentConceptAndSource() {
        var chunk = chunk(); var scope = KnowledgeScope.fromChunk(chunk);
        var duplicate = new DataIntegrityViolationException("opaque", new SQLException("opaque", "23000", 1062));
        when(helper.insertConceptAndSource(any(), eq(chunk), anyString(), anyDouble(), eq(scope))).thenThrow(duplicate);
        Concept existing = new Concept(); existing.setId(42L); existing.setDocument(chunk.getDocument());
        when(helper.findExisting(scope, "java")).thenReturn(Optional.of(existing));
        ConceptSource source = new ConceptSource(); source.setId(43L); source.setConcept(existing);
        when(helper.insertSourceOnly(existing, chunk, "Java", .9, scope, "description")).thenReturn(source);
        assertSame(source, service().getOrCreateWithSource(scope, "Java", "description", "TERM", chunk, "Java", .9));
    }
    @ParameterizedTest @ValueSource(ints = {1048,1452,1406})
    void unrelatedIntegrityPropagatesExactOriginalWithoutRecovery(int vendorCode) {
        var chunk = chunk(); var scope = KnowledgeScope.fromChunk(chunk);
        var original = new DataIntegrityViolationException("opaque", new SQLException("opaque", "23000", vendorCode));
        when(helper.insertConceptAndSource(any(), eq(chunk), anyString(), anyDouble(), eq(scope))).thenThrow(original);
        assertSame(original, assertThrows(DataIntegrityViolationException.class,
                () -> service().getOrCreateWithSource(scope, "Java", "description", "TERM", chunk, "Java", .9)));
        verify(helper, never()).findExisting(any(KnowledgeScope.class), anyString());
        verify(helper, never()).findExistingSource(anyLong(), anyLong());
    }
    @Test void nestedH2UniqueAndSqlNextExceptionsAreRecognizedWithoutMessageMatching() {
        assertTrue(KnowledgeGraphDuplicate.isDuplicate(new DataIntegrityViolationException("opaque",
                new RuntimeException(new SQLException("opaque", "23505", 23505)))));
        SQLException first = new SQLException("opaque", "HY000", 0);
        first.setNextException(new SQLException("opaque", "23000", 1062));
        assertTrue(KnowledgeGraphDuplicate.isDuplicate(first));
        assertFalse(KnowledgeGraphDuplicate.isDuplicate(new DataIntegrityViolationException("duplicate word is not proof")));
    }
    @Test void migrationOnlyRepairsNullabilityAndPreservesHostColumnType() throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/db/migration/V13__mind_map_v2_hardening.sql"));
        assertTrue(sql.contains("COLUMN_TYPE"));
        assertTrue(sql.contains("ALTER TABLE concepts MODIFY COLUMN notebook_id "));
        String operations = sql.replaceAll("(?m)--.*$", "").toUpperCase();
        assertFalse(operations.contains("DROP "));
        assertFalse(operations.contains("ADD COLUMN"));
        assertFalse(operations.contains("DELETE "));
        assertFalse(operations.contains("TRUNCATE"));
        assertFalse(operations.contains("FOREIGN_KEY_CHECKS"));
    }
}
