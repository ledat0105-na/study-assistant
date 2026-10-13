package com.example.studyassistant.domain.presentation;

import com.example.studyassistant.domain.presentation.controller.PresentationSlideEditorController;
import com.example.studyassistant.domain.presentation.dto.*;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.security.TeacherAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.example.studyassistant.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PresentationSlideEditorControllerTest {

    @Mock
        private UserRepository userRepository;

    @Mock
    private PresentationSlideEditorService slideEditorService;

        private TeacherAuthorizationService authService;
    private PresentationSlideEditorController controller;

    private MockMvc mockMvc;
    private User teacher;
    private User student;
    private MockHttpSession teacherSession;
    private MockHttpSession studentSession;

    @BeforeEach
    void setUp() {
                authService = new TeacherAuthorizationService(userRepository);
                controller = new PresentationSlideEditorController(authService, slideEditorService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new PresentationEditorExceptionHandler())
                .build();

        teacher = new User();
        teacher.setId(10L);
        teacher.setRole("TEACHER");
        teacher.setUsername("teacher");
        teacher.setEmail("teacher@test.com");
        teacher.setPlan("PREMIUM");

        student = new User();
        student.setId(11L);
        student.setRole("STUDENT");
        student.setUsername("student");
        student.setEmail("student@test.com");
        student.setPlan("FREE");

        teacherSession = new MockHttpSession();
        teacherSession.setAttribute("userId", teacher.getId());

        studentSession = new MockHttpSession();
        studentSession.setAttribute("userId", student.getId());
                lenient().when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
                lenient().when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
    }

    @Test
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/teacher/presentations/1/slides"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentRole_returns403() throws Exception {
        mockMvc.perform(get("/api/teacher/presentations/1/slides").session(studentSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void listSlides_returns200ForTeacher() throws Exception {
        when(slideEditorService.listSlides(eq(teacher), eq(1L))).thenReturn(List.of());

        mockMvc.perform(get("/api/teacher/presentations/1/slides").session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void patchSlide_returns200ForTeacher() throws Exception {
        when(slideEditorService.patchSlide(eq(teacher), eq(1L), eq(2L), any(SlidePatchRequest.class)))
                .thenReturn(new SlideReadDto(2L, 1L, 1, "TITLE_CONTENT", "Updated", "notes", "EDITED", 7L, 3L,
                        List.of(), List.of()));

        mockMvc.perform(patch("/api/teacher/presentations/1/slides/2")
                        .session(teacherSession)
                        .contentType("application/json")
                        .content("{\"title\":\"Updated\",\"speakerNotes\":\"notes\",\"expectedSlideVersion\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.generationState").value("EDITED"));
    }

    @Test
    void addElement_returns201ForTeacher() throws Exception {
        when(slideEditorService.addElement(eq(teacher), eq(1L), eq(2L), any(SlideElementCreateRequest.class)))
                .thenReturn(new SlideElementReadDto(5L, 2L, "TEXT", 0, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.TEN, new BigDecimal("8.00"), BigDecimal.ZERO, 9L,
                        Map.of("text", "hi"), Map.of("fontSize", 18), null, null, null, null, null, null));

        mockMvc.perform(post("/api/teacher/presentations/1/slides/2/elements")
                        .session(teacherSession)
                        .contentType("application/json")
                        .content("{\"elementType\":\"TEXT\",\"expectedSlideVersion\":7,\"x\":0,\"y\":0,\"width\":10,\"height\":8,\"rotation\":0,\"payload\":{\"text\":\"hi\"},\"style\":{\"fontSize\":18},\"zIndex\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.elementType").value("TEXT"))
                .andExpect(jsonPath("$.payload.text").value("hi"));
    }

    @Test
    void updateElement_returns200ForTeacher() throws Exception {
        when(slideEditorService.updateElement(eq(teacher), eq(1L), eq(2L), eq(5L), any(SlideElementUpdateRequest.class)))
                .thenReturn(new SlideElementReadDto(5L, 2L, "TEXT", 1, new BigDecimal("1.00"), new BigDecimal("2.00"),
                        new BigDecimal("10.00"), new BigDecimal("8.00"), BigDecimal.ZERO, 10L,
                        Map.of("text", "updated"), Map.of("fontSize", 18), null, null, null, null, null, null));

        mockMvc.perform(patch("/api/teacher/presentations/1/slides/2/elements/5")
                        .session(teacherSession)
                        .contentType("application/json")
                        .content("{\"elementType\":\"TEXT\",\"expectedSlideVersion\":7,\"expectedElementVersion\":9,\"x\":1,\"y\":2,\"width\":10,\"height\":8,\"rotation\":0,\"payload\":{\"text\":\"updated\"},\"style\":{\"fontSize\":18},\"zIndex\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.text").value("updated"));
    }

    @Test
    void deleteElement_returns204ForTeacher() throws Exception {
        mockMvc.perform(delete("/api/teacher/presentations/1/slides/2/elements/5")
                        .session(teacherSession)
                        .contentType("application/json")
                        .content("{\"expectedSlideVersion\":7,\"expectedElementVersion\":9}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void responseDtoDoesNotExposeStoredFileKeyOrOwnerData() throws Exception {
        when(slideEditorService.getSlide(eq(teacher), eq(1L), eq(2L)))
                .thenReturn(new SlideReadDto(2L, 1L, 1, "TITLE_CONTENT", "Title", "notes", "EDITED", 7L, 3L,
                        List.of(new SlideElementReadDto(5L, 2L, "IMAGE", 0, BigDecimal.ZERO, BigDecimal.ZERO,
                                new BigDecimal("10.00"), new BigDecimal("8.00"), BigDecimal.ZERO, 9L,
                                Map.of("alt", "img"), Map.of(), 99L, "a.png", "image/png", 200, 300,
                                "/api/teacher/presentations/1/media/99/content")),
                        List.of()));

        mockMvc.perform(get("/api/teacher/presentations/1/slides/2").session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("storedFileKey"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ownerId"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("filesystem"))));
    }

    @Test
    void foreignDeckSlideElementAndMediaReturn404() throws Exception {
        when(slideEditorService.listSlides(eq(teacher), eq(99L))).thenThrow(notFound());
        when(slideEditorService.getSlide(eq(teacher), eq(1L), eq(99L))).thenThrow(notFound());
        when(slideEditorService.updateElement(eq(teacher), eq(1L), eq(2L), eq(99L), any(SlideElementUpdateRequest.class)))
                .thenThrow(notFound());
        when(slideEditorService.addElement(eq(teacher), eq(1L), eq(2L), any(SlideElementCreateRequest.class)))
                .thenThrow(notFound());

        mockMvc.perform(get("/api/teacher/presentations/99/slides").session(teacherSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/teacher/presentations/1/slides/99").session(teacherSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/teacher/presentations/1/slides/2/elements/99")
                        .session(teacherSession).contentType("application/json")
                        .content("{\"elementType\":\"TEXT\",\"expectedSlideVersion\":1,\"expectedElementVersion\":1,\"x\":0,\"y\":0,\"width\":10,\"height\":8,\"payload\":{\"text\":\"x\"},\"style\":{}}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/teacher/presentations/1/slides/2/elements")
                        .session(teacherSession).contentType("application/json")
                        .content("{\"elementType\":\"IMAGE\",\"expectedSlideVersion\":1,\"x\":0,\"y\":0,\"width\":10,\"height\":8,\"payload\":{},\"style\":{},\"mediaAssetId\":99}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivedReadReturns200AndMutationReturns409() throws Exception {
        when(slideEditorService.getSlide(eq(teacher), eq(1L), eq(2L)))
                .thenReturn(new SlideReadDto(2L, 1L, 1, "TITLE_CONTENT", "Title", null, "GENERATED", 1L, 1L,
                        List.of(), List.of()));
        when(slideEditorService.patchSlide(eq(teacher), eq(1L), eq(2L), any(SlidePatchRequest.class)))
                .thenThrow(conflict());

        mockMvc.perform(get("/api/teacher/presentations/1/slides/2").session(teacherSession))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/teacher/presentations/1/slides/2")
                        .session(teacherSession).contentType("application/json")
                        .content("{\"title\":\"blocked\",\"expectedSlideVersion\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidGeometryAndTypedPayloadReturn400() throws Exception {
        when(slideEditorService.addElement(eq(teacher), eq(1L), eq(2L), any(SlideElementCreateRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid element"));

        mockMvc.perform(post("/api/teacher/presentations/1/slides/2/elements")
                        .session(teacherSession).contentType("application/json")
                        .content("{\"elementType\":\"TEXT\",\"expectedSlideVersion\":1,\"x\":-1,\"y\":0,\"width\":10,\"height\":8,\"payload\":{\"text\":\"x\"},\"style\":{}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/teacher/presentations/1/slides/2/elements")
                        .session(teacherSession).contentType("application/json")
                        .content("{\"elementType\":\"TEXT\",\"expectedSlideVersion\":1,\"x\":0,\"y\":0,\"width\":10,\"height\":8,\"payload\":{\"text\":\"x\"},\"style\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staleVersionReturns409() throws Exception {
        when(slideEditorService.patchSlide(eq(teacher), eq(1L), eq(2L), any(SlidePatchRequest.class)))
                .thenThrow(conflict());

        mockMvc.perform(patch("/api/teacher/presentations/1/slides/2")
                        .session(teacherSession).contentType("application/json")
                        .content("{\"title\":\"stale\",\"expectedSlideVersion\":0}"))
                .andExpect(status().isConflict());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Conflict");
    }
}
