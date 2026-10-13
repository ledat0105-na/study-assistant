package com.example.studyassistant.service;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.ConceptSource;
import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.DocumentChunk;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.ConceptRepository;
import com.example.studyassistant.repository.ConceptSourceRepository;
import com.example.studyassistant.repository.DocumentChunkRepository;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Tag("Integration")
public class ConceptServiceWithSourceIntegrationTest {

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private ConceptRepository conceptRepository;
    
    @Autowired
    private ConceptSourceRepository conceptSourceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private User user;
    private Notebook notebook;
    private Document document;
    private DocumentChunk chunk;
    private DocumentChunk chunk2;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        
        user = new User();
        user.setUsername("cs_src_user_" + suffix);
        user.setEmail("cs_src_" + suffix + "@example.com");
        user.setPassword("pass");
        user = userRepository.saveAndFlush(user);

        notebook = new Notebook();
        notebook.setTitle("Source Test Notebook");
        notebook.setUser(user);
        notebook = notebookRepository.saveAndFlush(notebook);

        document = new Document();
        document.setUser(user);
        document.setNotebook(notebook);
        document.setFileName("testdoc.pdf");
        document.setFilePath("/tmp/cs_test_" + suffix + ".pdf");
        document.setFileSize(1000L);
        document.setStatus("INDEXED");
        document.setActiveIndexVersion("v1");
        document = documentRepository.saveAndFlush(document);

        chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(0);
        chunk.setContent("Machine learning is great.");
        chunk.setTokenCount(10);
        chunk.setIndexVersion("v1");
        chunk = chunkRepository.saveAndFlush(chunk);

        chunk2 = new DocumentChunk();
        chunk2.setDocument(document);
        chunk2.setChunkIndex(1);
        chunk2.setContent("Deep learning is better.");
        chunk2.setTokenCount(10);
        chunk2.setIndexVersion("v1");
        chunk2 = chunkRepository.saveAndFlush(chunk2);
    }

    @AfterEach
    void tearDown() {
        if (notebook != null && notebook.getId() != null) {
            List<Concept> concepts = conceptRepository.findByNotebookId(notebook.getId());
            for (Concept c : concepts) {
                conceptSourceRepository.deleteAll(conceptSourceRepository.findByConceptId(c.getId()));
                conceptRepository.delete(c);
            }
        }
            
        if (chunk2 != null && chunk2.getId() != null) {
            chunkRepository.delete(chunk2);
        }
        if (chunk != null && chunk.getId() != null) {
            chunkRepository.delete(chunk);
        }
        if (document != null && document.getId() != null) {
            documentRepository.delete(document);
        }
        if (notebook != null && notebook.getId() != null) {
            notebookRepository.delete(notebook);
        }
        if (user != null && user.getId() != null) {
            userRepository.delete(user);
        }
    }

    // 1. new Concept + Source atomic commit
    @Test
    void test1_CreatesNewConceptAndSource() {
        ConceptSource source = conceptService.getOrCreateWithSource(
                notebook, "Machine Learning", "Desc", "TERM", chunk, "Machine learning is great.", 0.9
        );

        assertThat(source.getId()).isNotNull();
        assertThat(source.getConcept().getNormalizedName()).isEqualTo("machine learning");
        assertThat(conceptSourceRepository.findById(source.getId())).isPresent();
    }
    
    // 2. forced ConceptSource failure rolls back newly created Concept
    // 10. no orphan Concept after persistence failure
    @Test
    void test2_ForcedSourceFailureRollsBackConcept() {
        DocumentChunk detachedChunk = new DocumentChunk();
        detachedChunk.setId(99999L); // Invalid chunk ID to force FK violation

        assertThatThrownBy(() -> {
            conceptService.getOrCreateWithSource(
                    notebook, "Orphan Concept", "Desc", "TERM", detachedChunk, "evidence", 0.9
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
        
        // Ensure Concept was rolled back (no orphan)
        assertThat(conceptRepository.findByNotebookIdAndNormalizedName(notebook.getId(), "orphan concept")).isEmpty();
    }

    // 3. existing Concept unchanged
    // 4. existing Concept + missing source creates source
    @Test
    void test3and4_ExistingConceptUnchanged_And_CreatesNewSource() {
        ConceptSource src1 = conceptService.getOrCreateWithSource(
                notebook, "Deep Learning", "Original desc", "TERM", chunk, "evidence 1", 0.9
        );

        ConceptSource src2 = conceptService.getOrCreateWithSource(
                notebook, "DEEP LEARNING", "Modified desc", "TERM", chunk2, "evidence 2", 0.8
        );

        assertThat(src1.getConcept().getId()).isEqualTo(src2.getConcept().getId());
        assertThat(src2.getConcept().getDescription()).isEqualTo("Original desc"); // Unchanged
        assertThat(src1.getId()).isNotEqualTo(src2.getId()); // New source created for chunk2
        assertThat(conceptSourceRepository.findByConceptId(src1.getConcept().getId())).hasSize(2);
    }

    // 5. existing source idempotently reused
    @Test
    void test5_IdempotentReuse() {
        ConceptSource src1 = conceptService.getOrCreateWithSource(
                notebook, "Machine Learning", "Desc", "TERM", chunk, "Machine learning is great.", 0.9
        );
        
        ConceptSource src2 = conceptService.getOrCreateWithSource(
                notebook, "MACHINE LEARNING", "Desc", "TERM", chunk, "Machine learning is great.", 0.9
        );
        
        assertThat(src1.getConcept().getId()).isEqualTo(src2.getConcept().getId());
        assertThat(src1.getId()).isEqualTo(src2.getId());
        assertThat(conceptSourceRepository.findByConceptId(src1.getConcept().getId())).hasSize(1);
    }

    // 6. concurrent same Concept + same Chunk
    @Test
    void test6_ConcurrentSameConceptSameChunk() throws Exception {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<ConceptSource>> futures = new ArrayList<>();
        
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return conceptService.getOrCreateWithSource(
                        notebook, "Concurrent Concept", "Desc", "TERM", chunk, "evidence", 0.9
                );
            }));
        }
        
        List<ConceptSource> results = new ArrayList<>();
        for (Future<ConceptSource> f : futures) {
            results.add(f.get());
        }
        
        executor.shutdown();
        
        // All threads must succeed and return the exact same Concept and ConceptSource
        Long conceptId = results.get(0).getConcept().getId();
        Long sourceId = results.get(0).getId();
        
        for (ConceptSource s : results) {
            assertThat(s.getConcept().getId()).isEqualTo(conceptId);
            assertThat(s.getId()).isEqualTo(sourceId);
        }
        
        assertThat(conceptRepository.findByNotebookId(notebook.getId())).hasSize(1);
        assertThat(conceptSourceRepository.findByConceptId(conceptId)).hasSize(1);
    }

    // 7. concurrent same Concept + different Chunks
    @Test
    void test7_ConcurrentSameConceptDifferentChunks() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<ConceptSource>> futures = new ArrayList<>();
        
        futures.add(executor.submit(() -> {
            barrier.await();
            return conceptService.getOrCreateWithSource(
                    notebook, "Concurrent Concept 2", "Desc", "TERM", chunk, "evidence 1", 0.9
            );
        }));
        
        futures.add(executor.submit(() -> {
            barrier.await();
            return conceptService.getOrCreateWithSource(
                    notebook, "Concurrent Concept 2", "Desc", "TERM", chunk2, "evidence 2", 0.9
            );
        }));
        
        List<ConceptSource> results = new ArrayList<>();
        for (Future<ConceptSource> f : futures) {
            results.add(f.get());
        }
        
        executor.shutdown();
        
        Long conceptId = results.get(0).getConcept().getId();
        assertThat(results.get(1).getConcept().getId()).isEqualTo(conceptId);
        
        assertThat(results.get(0).getId()).isNotEqualTo(results.get(1).getId());
        
        assertThat(conceptRepository.findByNotebookId(notebook.getId())).hasSize(1);
        assertThat(conceptSourceRepository.findByConceptId(conceptId)).hasSize(2);
    }

    // 8. unrelated DataIntegrityViolationException is not swallowed
    @Test
    void test8_UnrelatedExceptionNotSwallowed() {
        Concept concept = new Concept();
        concept.setNotebook(notebook);
        concept.setCanonicalName("Will Fail");
        concept.setNormalizedName("will fail"); // Will trigger constraint on insertConceptAndSource
        concept.setDescription("Desc");
        conceptRepository.saveAndFlush(concept);

        DocumentChunk detachedChunk = new DocumentChunk();
        detachedChunk.setId(99999L); 

        // Both Concept is existing AND chunk is invalid.
        // It should fallback to insertSourceOnly, which will fail FK, and NOT be swallowed.
        assertThatThrownBy(() -> {
            conceptService.getOrCreateWithSource(
                    notebook, "Will Fail", "Desc", "TERM", detachedChunk, "evidence", 0.9
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // 9. outer transaction caller remains safe
    @Test
    void test9_OuterTransactionSafe() {
        // Because we use TransactionTemplate here, it simulates a caller with an active transaction
        // AFTER the parent fixtures (User, Notebook) have already been safely committed.
        // The getOrCreateWithSource must use REQUIRES_NEW and not poison this outer transaction on a duplicate.
        
        org.springframework.transaction.support.TransactionTemplate transactionTemplate = 
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                
        transactionTemplate.execute(status -> {
            ConceptSource src1 = conceptService.getOrCreateWithSource(
                    notebook, "Outer Safe", "Desc", "TERM", chunk, "evidence", 0.9
            );
            
            ConceptSource src2 = conceptService.getOrCreateWithSource(
                    notebook, "Outer Safe", "Desc", "TERM", chunk, "evidence", 0.9
            );
            
            assertThat(src1.getId()).isEqualTo(src2.getId());
            
            // Prove outer transaction is still valid by doing something
            notebook.setTitle("Updated Title");
            notebookRepository.saveAndFlush(notebook);
            assertThat(notebookRepository.findById(notebook.getId()).get().getTitle()).isEqualTo("Updated Title");
            
            return null;
        });
    }
}
