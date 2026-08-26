package com.example.studyassistant.repository;

import com.example.studyassistant.entity.PricingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingPlanRepository extends JpaRepository<PricingPlan, Long> {
    Optional<PricingPlan> findByPlanCode(String planCode);
    List<PricingPlan> findByIsActiveTrueOrderByIdAsc();
}
