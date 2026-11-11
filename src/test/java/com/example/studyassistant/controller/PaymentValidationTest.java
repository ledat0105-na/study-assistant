package com.example.studyassistant.controller;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentValidationTest {
    @Test void unknownPlanCannotCreateZeroPricePayment() {
        var pricing=mock(PricingPlanRepository.class);var payments=mock(PaymentRepository.class);
        var controller=new PaymentController();ReflectionTestUtils.setField(controller,"pricingPlanRepository",pricing);
        ReflectionTestUtils.setField(controller,"paymentRepository",payments);
        when(pricing.findByPlanCode("UNKNOWN")).thenReturn(Optional.empty());
        var request=new MockHttpServletRequest();request.getSession().setAttribute("userId",7L);
        assertThat(controller.checkoutUpgrade(Map.of("planId","UNKNOWN"),request).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(payments);
    }
    @Test void signedMalformedWebhookReturns400WithoutTouchingPayments() {
        var controller=new PaymentController();var payments=mock(PaymentRepository.class);
        ReflectionTestUtils.setField(controller,"paymentRepository",payments);
        ReflectionTestUtils.setField(controller,"webhookSecret","qa-test-secret");
        assertThat(controller.verifyPaymentWebhook(Map.of(),"qa-test-secret",new MockHttpServletRequest()).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(payments);
    }
    @Test void normalUserCannotReadPendingPaymentsOrApproveThem() {
        var controller=new PaymentController();var users=mock(UserRepository.class);var payments=mock(PaymentRepository.class);
        ReflectionTestUtils.setField(controller,"userRepository",users);ReflectionTestUtils.setField(controller,"paymentRepository",payments);
        var user=new User();user.setId(7L);user.setRole("STUDENT");when(users.findById(7L)).thenReturn(Optional.of(user));
        var request=new MockHttpServletRequest();request.getSession().setAttribute("userId",7L);
        assertThat(controller.pendingPayments(request).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.approvePayment(1L,request).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(payments);
    }
}
