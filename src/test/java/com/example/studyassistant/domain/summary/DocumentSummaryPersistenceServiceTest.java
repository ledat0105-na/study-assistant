package com.example.studyassistant.domain.summary;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.DocumentSummary;
import com.example.studyassistant.entity.DocumentSummarySource;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.DocumentSummaryRepository;
import com.example.studyassistant.repository.DocumentSummarySourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentSummaryPersistenceServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkRepository chunkRepository;
    @Mock
    private DocumentSummaryRepository summaryRepository;
    @Mock
    private DocumentSummarySourceRepository sourceRepository;

    private DocumentSummaryPersistenceService persistenceService;
    private Document document;

    @BeforeEach
    void setUp() {
        persistenceService = new DocumentSummaryPersistenceService(documentRepository, chunkRepository,
                summaryRepository, sourceRepository, new ObjectMapper());
        User owner = new User();
        owner.setId(7L);
        document = new Document();
        document.setId(42L);
        document.setUser(owner);
        document.setStatus("READY");
        document.setActiveIndexVersion("index-v2");
        document.setContentHash("hash-v2");
        lenient().when(documentRepository.findByIdWithPessimisticLock(42L)).thenReturn(Optional.of(document));
    }

    @Test
    void cacheReadRejectsForeignDocumentWithoutLookingUpSummary() {
        when(documentRepository.findByIdAndUserId(42L, 8L)).thenReturn(Optional.empty());
        assertThrows(DocumentSummaryNotFoundException.class, () -> persistenceService.getCached(8L,42L));
        verifyNoInteractions(summaryRepository,sourceRepository);
    }

    @Test
    void cacheReadRejectsProcessingOrMissingActiveVersion() {
        when(documentRepository.findByIdAndUserId(42L,7L)).thenReturn(Optional.of(document));
        document.setStatus("PROCESSING");
        assertThrows(DocumentSummaryUnavailableException.class, () -> persistenceService.getCached(7L,42L));
        document.setStatus("READY"); document.setActiveIndexVersion(null);
        assertThrows(DocumentSummaryUnavailableException.class, () -> persistenceService.getCached(7L,42L));
        verifyNoInteractions(summaryRepository,sourceRepository);
    }

    @Test
    void validGeneratedSummaryPersistsContentAndVerifiedSourcesAndReturnsThem() {
        SummaryContent content = new SummaryContent("overview", List.of(new SummaryContent.KeyPoint("point",List.of("S1"))),List.of(),"conclusion",List.of("S1"));
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(5L); chunk.setDocument(document); chunk.setPageStart(1); chunk.setPageEnd(2); chunk.setSectionTitle("Section");
        when(chunkRepository.findByIdAndDocumentIdAndIndexVersion(5L,42L,"index-v2")).thenReturn(Optional.of(chunk));
        when(summaryRepository.findByDocumentIdAndIndexVersion(42L,"index-v2")).thenReturn(Optional.empty());
        when(summaryRepository.saveAndFlush(any())).thenAnswer(i -> { DocumentSummary summary=i.getArgument(0); summary.setId(90L); return summary; });
        java.util.List<DocumentSummarySource> savedSources = new java.util.ArrayList<>();
        when(sourceRepository.save(any())).thenAnswer(i -> { DocumentSummarySource source=i.getArgument(0); savedSources.add(source); return source; });
        when(sourceRepository.findBySummaryIdOrderBySourceOrderAsc(90L)).thenReturn(savedSources);
        SummaryResponse response = persistenceService.persist(7L,42L,"index-v2","hash-v2",content,
                List.of(new SummaryCitation("S1",5L,42L,1,2,"Section")),"test-model");
        assertFalse(response.cached()); assertEquals(content,response.content()); assertEquals(42L,response.docId());
        assertTrue(response.summary().contains("overview")); assertEquals(1,response.citations().size());
        verify(summaryRepository).saveAndFlush(any()); verify(sourceRepository).save(any());
    }

    @Test
    void rejectsResultWhenIndexChangesBeforePersistAndLeavesCacheAlone() {
        SummaryContent content = new SummaryContent("overview",
                List.of(new SummaryContent.KeyPoint("point", List.of("S1"))),
                List.of(), "conclusion", List.of("S1"));

        assertThrows(StaleDocumentSummaryException.class, () -> persistenceService.persist(
                7L, 42L, "index-v1", "hash-v2", content, List.of(), "gpt-4o-mini"));

        verifyNoInteractions(chunkRepository, summaryRepository, sourceRepository);
    }

    @Test
    void rejectsForeignOwnerAndDoesNotPersist() {
        assertThrows(DocumentSummaryNotFoundException.class, () -> persistenceService.persist(
                8L, 42L, "index-v2", "hash-v2",
                new SummaryContent("overview", List.of(), List.of(), "conclusion", List.of("S1")),
                List.of(new SummaryCitation("S1", 5L, 42L, 1, 1, "Section")), "gpt-4o-mini"));

        verifyNoInteractions(chunkRepository, summaryRepository, sourceRepository);
    }

    @Test
    void ignoresLegacyAndOldIndexCacheButReplaysCurrentVersionCache() throws Exception {
        document.setAiSummary("legacy summary must not be trusted");
        when(documentRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(document));
        DocumentSummary cached = new DocumentSummary();
        cached.setId(90L);
        cached.setIndexVersion("index-v2");
        cached.setDocumentContentHash("hash-v2");
        cached.setSummaryText("cached structured summary");
        cached.setSummaryJson(new ObjectMapper().writeValueAsString(new SummaryContent(
                "overview", List.of(new SummaryContent.KeyPoint("point", List.of("S1"))),
                List.of(), "conclusion", List.of("S1"))));
        cached.setGeneratedAt(java.time.LocalDateTime.now());
        doReturn(Optional.empty()).doReturn(Optional.of(cached))
                .when(summaryRepository).findByDocumentIdAndIndexVersion(42L, "index-v2");
        assertTrue(persistenceService.getCached(7L, 42L).isEmpty());
        verify(summaryRepository).findByDocumentIdAndIndexVersion(42L, "index-v2");
        verifyNoInteractions(sourceRepository);

        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(5L);
        DocumentSummarySource source = new DocumentSummarySource();
        source.setSourceAlias("S1");
        source.setSourceOrder(0);
        source.setDocumentChunk(chunk);
        source.setPageStart(3);
        source.setPageEnd(4);
        source.setSectionTitle("Active section");
        when(sourceRepository.findBySummaryIdOrderBySourceOrderAsc(90L)).thenReturn(List.of(source));

        SummaryResponse response = persistenceService.getCached(7L, 42L).orElseThrow();
        assertTrue(response.cached());
        assertEquals("index-v2", response.indexVersion());
        assertEquals("S1", response.citations().get(0).sourceId());
        assertEquals(3, response.citations().get(0).pageStart());
        assertEquals(4, response.citations().get(0).pageEnd());
    }

    @Test
    void rejectsPersistWhenCitedChunkNoLongerMatchesSnapshot() {
        SummaryContent content = new SummaryContent("overview",
                List.of(new SummaryContent.KeyPoint("point", List.of("S1"))),
                List.of(), "conclusion", List.of("S1"));
        when(chunkRepository.findByIdAndDocumentIdAndIndexVersion(5L, 42L, "index-v2"))
                .thenReturn(Optional.empty());

        assertThrows(StaleDocumentSummaryException.class, () -> persistenceService.persist(
                7L, 42L, "index-v2", "hash-v2", content,
                List.of(new SummaryCitation("S1", 5L, 42L, 3, 4, "Section")), "gpt-4o-mini"));
        verifyNoInteractions(summaryRepository, sourceRepository);
    }

    @Test
    void rejectsCacheWhenDocumentContentHashNoLongerMatches() {
        when(documentRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(document));
        DocumentSummary cached = new DocumentSummary();
        cached.setId(90L);
        cached.setIndexVersion("index-v2");
        cached.setDocumentContentHash("older-hash");
        when(summaryRepository.findByDocumentIdAndIndexVersion(42L, "index-v2"))
                .thenReturn(Optional.of(cached));

        assertTrue(persistenceService.getCached(7L, 42L).isEmpty());
        verifyNoInteractions(sourceRepository);
    }
}
