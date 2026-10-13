package com.example.studyassistant.domain.presentation.media;

import com.example.studyassistant.domain.presentation.controller.PresentationMediaController;
import com.example.studyassistant.domain.presentation.dto.PresentationMediaAssetDto;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.security.TeacherAuthorizationService;
import com.example.studyassistant.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresentationMediaControllerTest {
    private TeacherAuthorizationService authorization;
    private UserRepository userRepository;
    private PresentationMediaService mediaService;
    private PresentationMediaController controller;
    private User teacher;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        mediaService = mock(PresentationMediaService.class);
        authorization = new TeacherAuthorizationService(userRepository);
        controller = new PresentationMediaController(authorization, mediaService);
        teacher = new User();
        teacher.setId(6L);
        teacher.setRole("TEACHER");
        request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("userId", teacher.getId());
        when(userRepository.findById(teacher.getId())).thenReturn(java.util.Optional.of(teacher));
    }

    @Test
    void K_foreignDeckIsNotFound() {
        when(mediaService.list(teacher, 99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> controller.list(99L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void L_studentCapabilityIsForbidden() {
        teacher.setRole("STUDENT");
        assertThatThrownBy(() -> controller.list(2L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void M_unauthenticatedRequestIsUnauthorized() {
        request = new MockHttpServletRequest();
        assertThatThrownBy(() -> controller.list(2L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void N_listUsesAuthenticatedOwner() {
        when(mediaService.list(teacher, 12L)).thenReturn(List.of());
        assertThat(controller.list(12L, request).getBody()).isEmpty();
        verify(mediaService).list(teacher, 12L);
    }

    @Test
    void O_contentStreamsAuthoritativeBytesAndSecurityHeaders() throws Exception {
        byte[] bytes = pngBytes();
        when(mediaService.openContent(teacher, 12L, 16L)).thenReturn(
                new PresentationMediaContent("image/png", bytes.length, "teacher-image.png", new ByteArrayInputStream(bytes)));

        var response = controller.content(12L, 16L, request);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(bytes.length);
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getCacheControl()).contains("private").contains("no-store");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("teacher-image.png");
        assertThat(response.getBody().getInputStream().readAllBytes()).containsExactly(bytes);
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x55aacc);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
        return output.toByteArray();
    }
}
