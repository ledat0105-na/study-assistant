package com.example.studyassistant.domain.presentation.generation;

import com.example.studyassistant.domain.presentation.*;
import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PresentationGenerationReliabilityTest {
    private TransactionTemplate transactions() {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        doAnswer(i -> { ((Consumer<org.springframework.transaction.TransactionStatus>) i.getArgument(0)).accept(null); return null; })
                .when(tx).executeWithoutResult(any());
        when(tx.execute(any())).thenAnswer(i -> ((org.springframework.transaction.support.TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
        return tx;
    }

    @Test void outlineFailureRefundsReservationDateExactlyOnce() {
        var jobs = mock(PresentationGenerationJobRepository.class);
        var users = mock(UserRepository.class);
        var usages = mock(UserDailyUsageRepository.class);
        User owner = new User(); owner.setId(7L);
        PresentationGenerationJob job = new PresentationGenerationJob();
        job.setOwner(owner); job.setStatus(PresentationJobStatus.RUNNING); job.setJobType(PresentationJobType.OUTLINE);
        job.setCreatedAt(LocalDateTime.of(2026, 10, 10, 23, 59));
        UserDailyUsage usage = new UserDailyUsage(); usage.setPresentationOutlineCount(2);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(jobs.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(owner));
        when(usages.findByUserIdAndUsageDateForUpdate(7L, LocalDate.of(2026, 10, 10))).thenReturn(Optional.of(usage));
        var service = new PresentationJobFinalizerService(users, jobs, usages, transactions(), Clock.systemUTC());
        service.failOutlineJob(1L, "LLM_ERROR", "failure");
        service.failOutlineJob(1L, "LLM_ERROR", "failure");
        assertThat(job.getStatus()).isEqualTo(PresentationJobStatus.FAILED);
        assertThat(usage.getPresentationOutlineCount()).isEqualTo(1);
        verify(usages, times(1)).save(usage);
    }

    @Test void coverageIncludesBeginningMiddleAndEndAndExcludesUnrequestedDocuments() {
        var docs = mock(DocumentRepository.class); var chunks = mock(DocumentChunkRepository.class);
        Document doc = new Document(); doc.setId(10L); doc.setStatus("READY"); doc.setActiveIndexVersion("v1");
        Document other = new Document(); other.setId(11L); other.setStatus("READY"); other.setActiveIndexVersion("v1");
        when(docs.findByUserIdAndNotebookId(7L, 2L)).thenReturn(List.of(other, doc));
        List<DocumentChunk> active = new ArrayList<>();
        for (int i=0; i<100; i++) { DocumentChunk c = new DocumentChunk(); c.setId((long)i+1); c.setChunkIndex(i); c.setContent("Đoạn " + i); active.add(c); }
        when(chunks.findByDocumentIdAndIndexVersion(10L, "v1")).thenReturn(active);
        var evidence = new PresentationEvidenceSelector(docs, chunks).select(7L, 2L, List.of(10L));
        assertThat(evidence).hasSize(16);
        assertThat(evidence.get(0).getChunkIndex()).isZero();
        assertThat(evidence.get(15).getChunkIndex()).isEqualTo(99);
        assertThat(evidence).anyMatch(c -> c.getChunkIndex() > 40 && c.getChunkIndex() < 60);
        verify(chunks, never()).findByDocumentIdAndIndexVersion(11L, "v1");
    }

    @Test void approvedPlanRetainsPurposeLanguageAndExactChunk() {
        String outline = """
            {"schemaVersion":1,"title":"Bài học","language":"vi","sections":[{"slides":[
            {"title":"Activity","purpose":"Giải thích vòng đời","slideType":"CONTENT","keyPoints":["onCreate"],"sourceRefs":["S4"]}]}]}
            """;
        String snapshot = """
            {"sources":[{"alias":"S4","chunkId":44,"documentId":10,"indexVersion":"v1"}]}
            """;
        PlannedSlide plan = new OutlineFlatteningService(new ObjectMapper()).flattenOutline(outline, snapshot).get(0);
        assertThat(plan.purpose()).isEqualTo("Giải thích vòng đời");
        assertThat(plan.language()).isEqualTo("vi");
        assertThat(plan.approvedSources()).containsExactly(new PlannedSlide.ApprovedSource("S4",44L,10L,"v1"));
        assertThatThrownBy(() -> new OutlineFlatteningService(new ObjectMapper()).flattenOutline(outline, "{\"sources\":[]}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void approvedEvidenceUsesExactChunkAndFailsWhenIndexChanges() {
        var docs = mock(DocumentRepository.class); var chunks = mock(DocumentChunkRepository.class);
        var retrieval = mock(com.example.studyassistant.retrieval.service.RetrievalService.class);
        User owner = new User(); owner.setId(7L);
        Notebook notebook = new Notebook(); notebook.setId(2L);
        Document doc = new Document(); doc.setId(10L); doc.setUser(owner); doc.setNotebook(notebook);
        doc.setStatus("READY"); doc.setActiveIndexVersion("v1");
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(44L); chunk.setDocument(doc); chunk.setIndexVersion("v1"); chunk.setContent("Nguồn đã duyệt");
        when(docs.findById(10L)).thenReturn(Optional.of(doc)); when(chunks.findById(44L)).thenReturn(Optional.of(chunk));
        var processor = new PresentationSlideGenerationProcessor(null,null,null,null,null,null,docs,chunks,null,null,retrieval,null,null,null,new ObjectMapper());
        var plan = new PlannedSlide(1,"Title","CONTENT",List.of(),"Purpose","vi",List.of(new PlannedSlide.ApprovedSource("S4",44L,10L,"v1")));
        assertThat(processor.approvedEvidence(7L,2L,List.of(10L),plan)).extracting(c -> c.getChunkId()).containsExactly(44L);
        verifyNoInteractions(retrieval);
        doc.setActiveIndexVersion("v2");
        assertThatThrownBy(() -> processor.approvedEvidence(7L,2L,List.of(10L),plan))
                .isInstanceOf(SlideGenerationException.SlideJobFailedFastException.class);
    }

    @Test void failedSlidesReleaseOnlyUnusedQuotaAndTerminalJobIsNotRefundedTwice() {
        var jobs = mock(PresentationGenerationJobRepository.class); var users = mock(UserRepository.class); var usages = mock(UserDailyUsageRepository.class);
        User owner = new User(); owner.setId(7L);
        PresentationGenerationJob job = new PresentationGenerationJob(); job.setStatus(PresentationJobStatus.RUNNING);
        job.setCreatedAt(LocalDateTime.of(2026,10,10,12,0)); job.setProgressTotal(5); job.setProgressCurrent(3);
        UserDailyUsage usage = new UserDailyUsage(); usage.setPresentationSlideCount(8);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(owner)); when(jobs.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
        when(usages.findByUserIdAndUsageDateForUpdate(7L,LocalDate.of(2026,10,10))).thenReturn(Optional.of(usage));
        var service = new PresentationJobFinalizerService(users,jobs,usages,transactions(),Clock.systemUTC());
        service.finalizeSlideJob(1L,7L,PresentationJobStatus.FAILED,"PARTIAL_SLIDE_FAILURE","two failed");
        service.finalizeSlideJob(1L,7L,PresentationJobStatus.FAILED,"PARTIAL_SLIDE_FAILURE","two failed");
        assertThat(usage.getPresentationSlideCount()).isEqualTo(6);
        verify(usages,times(1)).save(usage);
    }

    @Test void failedSlideIsRetriedOnceAndFollowingSlideStillRuns() throws Exception {
        var jobs = mock(PresentationGenerationJobRepository.class); var finalizer = mock(PresentationJobFinalizerService.class);
        PresentationGenerationJob job = new PresentationGenerationJob(); job.setStatus(PresentationJobStatus.QUEUED); job.setDeckId(2L);
        when(jobs.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
        User owner = new User(); owner.setId(7L); job.setOwner(owner); job.setOutlineRevisionId(3L);
        var processor = spy(new PresentationSlideGenerationProcessor(jobs, mock(PresentationDeckRepository.class), null,
                mock(PresentationSlideRepository.class), null,null,null,null,transactions(),null,null,finalizer,Clock.systemUTC(),null,new ObjectMapper()));
        PlannedSlide bad = new PlannedSlide(1,"Bad","CONTENT",List.of());
        PlannedSlide good = new PlannedSlide(2,"Good","CONTENT",List.of());
        doThrow(new RuntimeException("provider failure")).when(processor).processSingleSlide(1L,2L,7L,4L,List.of(10L),3L,"hash",bad);
        doNothing().when(processor).processSingleSlide(1L,2L,7L,4L,List.of(10L),3L,"hash",good);
        processor.processJob(1L,2L,7L,4L,List.of(10L),3L,"hash",List.of(bad,good));
        verify(processor,times(2)).processSingleSlide(1L,2L,7L,4L,List.of(10L),3L,"hash",bad);
        verify(processor).processSingleSlide(1L,2L,7L,4L,List.of(10L),3L,"hash",good);
        verify(finalizer).finalizeSlideJob(eq(1L),eq(7L),eq(PresentationJobStatus.FAILED),eq("PARTIAL_SLIDE_FAILURE"),anyString());
    }

    @Test void alreadyRunningOrTerminalJobDoesNotStartAnotherWorker() throws Exception {
        for (var state : List.of(PresentationJobStatus.RUNNING, PresentationJobStatus.CANCELLED, PresentationJobStatus.SUCCEEDED)) {
            var jobs = mock(PresentationGenerationJobRepository.class);
            PresentationGenerationJob job = new PresentationGenerationJob(); job.setStatus(state);
            when(jobs.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
            var finalizer = mock(PresentationJobFinalizerService.class);
            var processor = spy(new PresentationSlideGenerationProcessor(jobs,null,null,null,null,null,null,null,
                    transactions(),null,null,finalizer,Clock.systemUTC(),null,new ObjectMapper()));
            PlannedSlide plan = new PlannedSlide(1,"Title","CONTENT",List.of());
            processor.processJob(1L,2L,7L,4L,List.of(10L),3L,"hash",List.of(plan));
            verify(processor,never()).processSingleSlide(anyLong(),anyLong(),anyLong(),anyLong(),anyList(),anyLong(),anyString(),any());
            verifyNoInteractions(finalizer);
            assertThat(job.getStatus()).isEqualTo(state);
        }
    }
}
