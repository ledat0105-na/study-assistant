package com.example.studyassistant.controller;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizDocumentOptionsTest {
    @Mock DocumentRepository documents;
    @Mock QuizRepository quizzes;
    @Mock QuizQuestionRepository questions;
    @Mock QuizQuestionOptionRepository options;
    @Mock HttpServletRequest request;
    @Mock HttpSession session;
    @InjectMocks QuizController controller;
    @Mock com.example.studyassistant.domain.quiz.generation.GroundedQuizGenerationService generation;

    @Test void viewerJsonWithoutUserIdBindsAndUsesSessionOwner() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var loggedIn = new org.springframework.mock.web.MockHttpSession(); loggedIn.setAttribute("userId", 1L);
        when(generation.generate(any())).thenReturn(
                new com.example.studyassistant.domain.quiz.generation.GroundedQuizGenerationResult(20L, 5, 0, List.of()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/quizzes/generate")
                .session(loggedIn).contentType("application/json").content("""
                {"documentId":10,"scopeType":"DOCUMENT","mode":"PRACTICE",
                 "allowedQuestionTypes":["MCQ_SINGLE"]}
                """)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(generation).generate(argThat(command -> command.userId() == 1L && command.questionCount() == null));
    }

    @Test void clientCannotOverrideSessionOwner() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var loggedIn = new org.springframework.mock.web.MockHttpSession(); loggedIn.setAttribute("userId", 1L);
        when(generation.generate(any())).thenReturn(
                new com.example.studyassistant.domain.quiz.generation.GroundedQuizGenerationResult(20L, 5, 0, List.of()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/quizzes/generate")
                .session(loggedIn).contentType("application/json").content("""
                {"userId":999,"documentId":10,"scopeType":"DOCUMENT","mode":"PRACTICE",
                 "questionCount":5,"allowedQuestionTypes":["MCQ_SINGLE"]}
                """)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(generation).generate(argThat(command -> command.userId() == 1L));
    }

    @Test void canonicalReadIncludesOrderedOptionTextWithoutCorrectAnswers() {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(1L);
        when(documents.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(new Document()));
        Quiz quiz = new Quiz(); quiz.setId(20L);
        when(quizzes.findByDocumentId(10L)).thenReturn(List.of(quiz));
        QuizQuestion question = new QuizQuestion(); question.setId(30L);
        question.setQuestionType(QuizQuestionType.MCQ_SINGLE); question.setQuestion("Q");
        question.setCorrectAnswer("A"); question.setExplanation("Hidden");
        when(questions.findByQuizId(20L)).thenReturn(List.of(question));
        QuizQuestionOption first = new QuizQuestionOption(); first.setOptionText("Yes"); first.setIsCorrect(true);
        QuizQuestionOption second = new QuizQuestionOption(); second.setOptionText("No"); second.setIsCorrect(false);
        when(options.findByQuestionIdOrderByOptionOrderAsc(30L)).thenReturn(List.of(first, second));
        Map<?, ?> body = (Map<?, ?>) controller.getOrGenerateQuizForDocument(10L, request).getBody();
        Map<?, ?> result = (Map<?, ?>) ((List<?>) body.get("questions")).get(0);
        assertEquals(List.of("Yes", "No"), result.get("options"));
        assertFalse(result.containsKey("correctAnswer")); assertFalse(result.containsKey("explanation"));
    }

    @Test void documentReadReturnsNewestQuizWithoutRemovingOldQuiz() {
        when(request.getSession(false)).thenReturn(session); when(session.getAttribute("userId")).thenReturn(1L);
        when(documents.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(new Document()));
        Quiz old = new Quiz(); old.setId(20L); Quiz latest = new Quiz(); latest.setId(25L);
        when(quizzes.findByDocumentId(10L)).thenReturn(List.of(old, latest));
        when(questions.findByQuizId(25L)).thenReturn(List.of());
        Map<?, ?> body = (Map<?, ?>) controller.getOrGenerateQuizForDocument(10L, request).getBody();
        assertEquals(25L, body.get("quizId"));
        verify(questions, never()).findByQuizId(20L); verify(quizzes, never()).delete(any());
    }
}
