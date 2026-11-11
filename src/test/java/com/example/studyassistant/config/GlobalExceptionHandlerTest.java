package com.example.studyassistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @RestController
    static class FailingController {
        @GetMapping("/test/fail") String fail() { throw new IllegalStateException("private database detail"); }
        @GetMapping("/test/ok") String ok() { return "ok"; }
        @GetMapping("/test/number") int number(@RequestParam("id") int id) { return id; }
        @PostMapping("/test/body") String body(@RequestBody java.util.Map<String,String> body) { return "ok"; }
    }
    @Test void unexpectedFailureIsSafeAndNextRequestStillWorks() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new ServerErrorLoggingFilter(new com.example.studyassistant.service.IncidentMonitor())).build();
        mvc.perform(get("/test/fail")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database detail"))));
        mvc.perform(get("/test/ok")).andExpect(status().isOk()).andExpect(content().string("ok"));
    }
    @Test void frameworkClientErrorsKeepCorrectStatus() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/test/number").param("id","bad")).andExpect(status().isBadRequest());
        mvc.perform(post("/test/body").contentType("application/json").content("{")).andExpect(status().isBadRequest());
        mvc.perform(post("/test/ok")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/test/missing")).andExpect(status().isNotFound());
    }
}
