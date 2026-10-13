package com.example.studyassistant.controller;

import com.example.studyassistant.domain.quiz.submission.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.NoSuchElementException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizAttemptSubmitContractTest {
    private final QuizAttemptSubmissionService service = mock(QuizAttemptSubmissionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new QuizAttemptController(service)).build();
    private static final String BODY = """
        {"submissionToken":"210cc85e-eb31-482f-8703-a26ab56b6b55",
         "answers":[{"questionOrder":7,"selectedOptionOrders":[30],"shortAnswerText":null}]}
        """;
    @Test
    void acceptsActualHttpPayloadWithoutUserOrAttemptIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession(); session.setAttribute("userId", 42L);
        when(service.submit(any())).thenReturn(new QuizAttemptResultResponse(55L, 9L, "COMPLETED", 100.0, 1, 1, null, List.of()));
        mvc.perform(post("/api/quiz-attempts/55/submit").session(session).contentType("application/json").content(BODY))
                .andExpect(status().isOk());
        ArgumentCaptor<QuizAttemptSubmitCommand> command = ArgumentCaptor.forClass(QuizAttemptSubmitCommand.class);
        verify(service).submit(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(42L);
        assertThat(command.getValue().attemptId()).isEqualTo(55L);
        assertThat(command.getValue().answers().get(0).questionOrder()).isEqualTo(7);
        assertThat(command.getValue().answers().get(0).selectedOptionOrders()).containsExactly(30);
    }
    @Test
    void sessionRequiredBeforeGrading() throws Exception {
        mvc.perform(post("/api/quiz-attempts/55/submit").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test
    void foreignAttemptNotFoundNeverReturnsScore() throws Exception {
        MockHttpSession session = new MockHttpSession(); session.setAttribute("userId", 2L);
        when(service.submit(any())).thenThrow(new NoSuchElementException("Attempt not found"));
        mvc.perform(post("/api/quiz-attempts/55/submit").session(session).contentType("application/json").content(BODY))
                .andExpect(status().isNotFound());
    }
}
