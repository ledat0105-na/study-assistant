package com.example.studyassistant.controller;

import com.example.studyassistant.entity.User;
import com.example.studyassistant.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthProfileControllerTest {
    @Test void profileRequiresSessionAndNeverLeaksCredentials() throws Exception {
        var service = mock(UserService.class);
        var user = new User(); user.setId(7L);user.setFullName("Học viên");user.setEmail("qa@example.com");
        user.setPassword("private-hash");user.setResetToken("private-reset-token");
        when(service.getUserById(7L)).thenReturn(user);
        var controller = new AuthController(); ReflectionTestUtils.setField(controller,"userService",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new com.example.studyassistant.config.GlobalExceptionHandler()).build();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").sessionAttr("userId",7L)).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Học viên")).andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.resetToken").doesNotExist());
    }
    @Test void invalidProfileIsRejectedBeforePersistence() throws Exception {
        var service=mock(UserService.class);var controller=new AuthController();
        ReflectionTestUtils.setField(controller,"userService",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new com.example.studyassistant.config.GlobalExceptionHandler()).build();
        mvc.perform(patch("/api/auth/profile").sessionAttr("userId",7L).contentType("application/json")
            .content("{\"name\":\"\",\"email\":\"bad-email\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
