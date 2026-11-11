package com.example.studyassistant.controller;

import com.example.studyassistant.entity.PricingPlan;
import com.example.studyassistant.repository.PricingPlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentPricingRegressionTest {
    @Test void viewingCatalogNeverOverwritesCustomVietnameseContent() {
        var repository = mock(PricingPlanRepository.class);
        var plan = new PricingPlan();
        plan.setPlanCode("PRO"); plan.setName("MÃ ƯU ĐÃI - Gói riêng");
        plan.setDescription("Nội dung do quản trị viên sửa");
        when(repository.findAll()).thenReturn(List.of(plan));
        when(repository.findByIsActiveTrueOrderByIdAsc()).thenReturn(List.of(plan));
        var controller = new PaymentController();
        ReflectionTestUtils.setField(controller,"pricingPlanRepository",repository);
        assertThat(controller.getPlans().getStatusCode().value()).isEqualTo(200);
        assertThat(plan.getName()).isEqualTo("MÃ ƯU ĐÃI - Gói riêng");
        assertThat(plan.getDescription()).isEqualTo("Nội dung do quản trị viên sửa");
        verify(repository,never()).save(any());
        verify(repository,never()).findByPlanCode(anyString());
    }
}
