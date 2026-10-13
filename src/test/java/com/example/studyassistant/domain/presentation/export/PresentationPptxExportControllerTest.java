package com.example.studyassistant.domain.presentation.export;

import com.example.studyassistant.domain.presentation.controller.PresentationPptxExportController;
import com.example.studyassistant.domain.presentation.controller.PresentationPptxExportExceptionHandler;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.security.TeacherAuthorizationService;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PresentationPptxExportControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private PresentationPptxExportService exportService;

    @TempDir Path temporaryDirectory;

    private MockMvc mockMvc;
    private User teacher;
    private User student;
    private MockHttpSession teacherSession;
    private MockHttpSession studentSession;

    @BeforeEach
    void setUp() {
        TeacherAuthorizationService authorizationService = new TeacherAuthorizationService(userRepository);
        PresentationPptxExportController controller = new PresentationPptxExportController(authorizationService, exportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new PresentationPptxExportExceptionHandler())
                .build();

        teacher = new User();
        teacher.setId(41L);
        teacher.setRole("TEACHER");
        student = new User();
        student.setId(42L);
        student.setRole("STUDENT");
        teacherSession = new MockHttpSession();
        teacherSession.setAttribute("userId", teacher.getId());
        studentSession = new MockHttpSession();
        studentSession.setAttribute("userId", student.getId());
        lenient().when(userRepository.findById(teacher.getId())).thenReturn(Optional.of(teacher));
        lenient().when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
    }

    @Test
    void teacherReceivesCompletePptxWithSafeHeadersAndSpoolIsRemoved() throws Exception {
        byte[] bytes = validPptx();
        Path spool = temporaryDirectory.resolve("private-export.pptx");
        Files.write(spool, bytes);
        when(exportService.export(teacher, 10L)).thenReturn(new PptxExportArtifact(spool, bytes.length, "deck_export.pptx"));

        var result = mockMvc.perform(get("/api/teacher/presentations/10/export/pptx").session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("deck_export.pptx")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andReturn();

        byte[] responseBytes = result.getResponse().getContentAsByteArray();
        try (XMLSlideShow reopened = new XMLSlideShow(new ByteArrayInputStream(responseBytes))) {
            assertThat(reopened.getSlides()).hasSize(1);
        }
        assertThat(Files.exists(spool)).isFalse();
        assertThat(new String(responseBytes, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("storedFileKey", temporaryDirectory.toString());
    }

    @Test
    void unauthenticatedAndStudentAreRejected() throws Exception {
        mockMvc.perform(get("/api/teacher/presentations/10/export/pptx"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/teacher/presentations/10/export/pptx").session(studentSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreignDeckAndInvalidStateReturnSafeErrors() throws Exception {
        when(exportService.export(eq(teacher), eq(99L)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
        mockMvc.perform(get("/api/teacher/presentations/99/export/pptx").session(teacherSession))
                .andExpect(status().isNotFound());

        when(exportService.export(eq(teacher), eq(10L)))
                .thenThrow(new PresentationExportStateInvalidException("private storage path must not leak"));
        var result = mockMvc.perform(get("/api/teacher/presentations/10/export/pptx").session(teacherSession))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/json"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("EXPORT_STATE_INVALID")
                .doesNotContain("private storage path", temporaryDirectory.toString());
    }

    private byte[] validPptx() throws Exception {
        try (XMLSlideShow presentation = new XMLSlideShow();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            presentation.createSlide();
            presentation.write(output);
            return output.toByteArray();
        }
    }
}
