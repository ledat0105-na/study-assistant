package com.example.studyassistant.entity;

import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("Integration")
@Transactional
public class QuizQuestionConceptAttributionMySQLIntegrationTest {

    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private ConceptRepository conceptRepository;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private UserRepository userRepository;

    private User owner;
    private Notebook notebook;
    private Concept concept;
    private Quiz quiz;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        owner = new User();
        owner.setUsername("owner_q_" + uniqueSuffix);
        owner.setEmail("owner_q_" + uniqueSuffix + "@example.com");
        owner.setPassword("hash");
        owner.setFullName("Owner");
        userRepository.save(owner);

        notebook = new Notebook();
        notebook.setUser(owner);
        notebook.setTitle("NB");
        notebook.setDescription("Desc");
        notebookRepository.save(notebook);

        concept = new Concept();
        concept.setNotebook(notebook);
        concept.setCanonicalName("Test Concept");
        concept.setNormalizedName("test concept");
        concept.setConceptType("CONCEPT");
        concept.setDescription("Desc");
        conceptRepository.save(concept);

        quiz = new Quiz();
        quiz.setTitle("Test Quiz");
        quizRepository.save(quiz);
    }

    @Test
    void testQuizQuestionMayReferenceConcept() {
        QuizQuestion q = new QuizQuestion();
        q.setQuiz(quiz);
        q.setQuestion("Q1");
        q.setAnswerA("A");
        q.setAnswerB("B");
        q.setAnswerC("C");
        q.setAnswerD("D");
        q.setCorrectAnswer("A");
        q.setConcept(concept); // New attribution

        QuizQuestion saved = quizQuestionRepository.save(q);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getConcept()).isNotNull();
        assertThat(saved.getConcept().getId()).isEqualTo(concept.getId());
    }

    @Test
    void testQuizQuestionNullableConceptRemainsValid() {
        QuizQuestion q = new QuizQuestion();
        q.setQuiz(quiz);
        q.setQuestion("Q2");
        q.setAnswerA("A");
        q.setAnswerB("B");
        q.setAnswerC("C");
        q.setAnswerD("D");
        q.setCorrectAnswer("A");
        // concept is null by default

        assertDoesNotThrow(() -> quizQuestionRepository.save(q));
        QuizQuestion saved = quizQuestionRepository.findById(q.getId()).orElseThrow();
        assertThat(saved.getConcept()).isNull();
    }

    @Test
    void testQuizQuestionDoesNotCascadeDeleteConcept() {
        QuizQuestion q = new QuizQuestion();
        q.setQuiz(quiz);
        q.setQuestion("Q3");
        q.setAnswerA("A");
        q.setAnswerB("B");
        q.setAnswerC("C");
        q.setAnswerD("D");
        q.setCorrectAnswer("A");
        q.setConcept(concept);
        quizQuestionRepository.save(q);

        quizQuestionRepository.delete(q);

        // Concept should still exist
        assertThat(conceptRepository.findById(concept.getId())).isPresent();
    }
}
