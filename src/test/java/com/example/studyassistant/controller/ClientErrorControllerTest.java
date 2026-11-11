package com.example.studyassistant.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class ClientErrorControllerTest {
    @Test void validReportsAreLoggedAndRateLimitedPerSession() {
        var controller = new ClientErrorController(new com.example.studyassistant.service.IncidentMonitor());
        var request = new MockHttpServletRequest();
        var error = new ClientErrorController.ClientError("SCRIPT_ERROR", "/js/test.js", 10, 2);
        for (int i=0; i<5; i++) assertThat(controller.report(error, request).getStatusCode().value()).isEqualTo(204);
        assertThat(controller.report(error, request).getStatusCode().value()).isEqualTo(429);
        assertThat(controller.report(error, new MockHttpServletRequest()).getStatusCode().value()).isEqualTo(204);
    }
    @Test void arbitraryTextAndPrivateQueryStringsAreRejected() {
        var controller = new ClientErrorController(new com.example.studyassistant.service.IncidentMonitor());
        for (String source : new String[]{"/file.js?key=secret", "bad\nlog", "x".repeat(201)}) {
            assertThat(controller.report(new ClientErrorController.ClientError("SCRIPT_ERROR",source,1,0),
                new MockHttpServletRequest()).getStatusCode().value()).isEqualTo(400);
        }
        assertThat(controller.report(new ClientErrorController.ClientError("arbitrary message","",0,0),
            new MockHttpServletRequest()).getStatusCode().value()).isEqualTo(400);
    }
}
