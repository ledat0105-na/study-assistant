package com.example.studyassistant.service;

import com.example.studyassistant.entity.Concept;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.ConceptRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ConceptService.getOrCreate().
 *
 * NO class-level @Transactional. Each test allows setup data to commit
 * before calling getOrCreate(), which internally uses
 * @Transactional(REQUIRES_NEW). A test-managed uncommitted Notebook row
 * would cause MySQL FK lock wait timeout (error 1205) when the inner
 * REQUIRES_NEW transaction attempts to validate the parent FK.
 */
@SpringBootTest
@Tag("Integration")
public class ConceptServiceIntegrationTest {

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    // Test-owned entities tracked for scoped cleanup
    private User user;
    private final List<Notebook> testNotebooks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Unique suffix per test run to avoid cross-run collisions
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Create and commit User — no surrounding test transaction,
        // so the Spring Data JPA default transaction commits immediately.
        user = new User();
        user.setUsername("csi_user_" + suffix);
        user.setEmail("csi_" + suffix + "@example.com");
        user.setPassword("hashedpass");
        user = userRepository.saveAndFlush(user);
        // user row is now committed; FK parent is visible to REQUIRES_NEW
    }

    /**
     * Creates a committed Notebook owned by the test user.
     * Registers it for scoped cleanup in @AfterEach.
     */
    private Notebook createNotebook(String title) {
        Notebook nb = new Notebook();
        nb.setTitle(title);
        nb.setUser(user);
        nb = notebookRepository.saveAndFlush(nb);
        // Notebook row is now committed
        testNotebooks.add(nb);
        return nb;
    }

    @AfterEach
    void tearDown() {
        // FK order: Concepts → Notebooks → User
        // Delete only test-owned rows.
        for (Notebook nb : testNotebooks) {
            if (nb.getId() != null) {
                List<Concept> concepts = conceptRepository.findByNotebookId(nb.getId());
                for (Concept c : concepts) {
                    conceptRepository.delete(c);
                }
                notebookRepository.delete(nb);
            }
        }
        testNotebooks.clear();

        if (user != null && user.getId() != null) {
            userRepository.delete(user);
            user = null;
        }
    }

    @Test
    void getOrCreate_CreatesNewConcept() {
        Notebook notebook = createNotebook("Notebook-Create");
        // Setup committed; REQUIRES_NEW insert can safely validate FK

        Concept concept = conceptService.getOrCreate(notebook, "Machine Learning", "Desc", "TOPIC");

        assertThat(concept.getId()).isNotNull();
        assertThat(concept.getNormalizedName()).isEqualTo("machine learning");
        assertThat(concept.getCanonicalName()).isEqualTo("Machine Learning");
        assertThat(conceptRepository.findById(concept.getId())).isPresent();
    }

    @Test
    void getOrCreate_DeduplicatesInSameNotebook() {
        Notebook notebook = createNotebook("Notebook-Dedup");

        Concept concept1 = conceptService.getOrCreate(notebook, "Machine Learning", "Desc 1", "TOPIC");
        Concept concept2 = conceptService.getOrCreate(notebook, "MACHINE   LEARNING", "Desc 2", "TOPIC");

        assertThat(concept1.getId()).isEqualTo(concept2.getId());
        assertThat(conceptRepository.findByNotebookId(notebook.getId())).hasSize(1);
    }

    @Test
    void getOrCreate_IsolatesDifferentNotebooks() {
        Notebook notebook1 = createNotebook("Notebook-Iso-1");
        Notebook notebook2 = createNotebook("Notebook-Iso-2");

        Concept concept1 = conceptService.getOrCreate(notebook1, "Machine Learning", "Desc", "TOPIC");
        Concept concept2 = conceptService.getOrCreate(notebook2, "machine learning", "Desc", "TOPIC");

        assertThat(concept1.getId()).isNotEqualTo(concept2.getId());
        assertThat(conceptRepository.findByNotebookId(notebook1.getId())).hasSize(1);
        assertThat(conceptRepository.findByNotebookId(notebook2.getId())).hasSize(1);
    }

    @Test
    void getOrCreate_ReturnsExistingUnchanged() {
        Notebook notebook = createNotebook("Notebook-Unchanged");

        Concept concept1 = conceptService.getOrCreate(notebook, "Machine Learning", "Original description", "TOPIC");
        Concept concept2 = conceptService.getOrCreate(notebook, "MACHINE LEARNING", "Replacement description", "TOPIC");

        assertThat(concept2.getId()).isEqualTo(concept1.getId());
        assertThat(concept2.getDescription()).isEqualTo("Original description");
        assertThat(concept2.getCanonicalName()).isEqualTo("Machine Learning");
    }

    @Test
    void getOrCreate_DisplayNormalization() {
        Notebook notebook = createNotebook("Notebook-Display");

        Concept concept = conceptService.getOrCreate(notebook, "  Machine    Learning  ", "Desc", "TOPIC");

        assertThat(concept.getCanonicalName()).isEqualTo("Machine Learning");
        assertThat(concept.getNormalizedName()).isEqualTo("machine learning");
    }

    @Test
    void getOrCreate_PunctuationSensitive() {
        Notebook notebook = createNotebook("Notebook-Punctuation");

        Concept c1 = conceptService.getOrCreate(notebook, "C", "Lang", "TOPIC");
        Concept c2 = conceptService.getOrCreate(notebook, "C++", "Lang", "TOPIC");
        Concept c3 = conceptService.getOrCreate(notebook, "C#", "Lang", "TOPIC");

        assertThat(c1.getId()).isNotEqualTo(c2.getId());
        assertThat(c1.getId()).isNotEqualTo(c3.getId());
        assertThat(c2.getId()).isNotEqualTo(c3.getId());
    }

    @Test
    void getOrCreate_VietnameseAccents() {
        Notebook notebook = createNotebook("Notebook-Vietnamese");

        Concept concept = conceptService.getOrCreate(notebook, " Học   Máy ", "Desc", "TOPIC");

        assertThat(concept.getCanonicalName()).isEqualTo("Học Máy");
        assertThat(concept.getNormalizedName()).isEqualTo("học máy");
    }
}
