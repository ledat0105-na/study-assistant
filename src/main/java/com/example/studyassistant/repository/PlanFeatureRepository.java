package com.example.studyassistant.repository;

import com.example.studyassistant.entity.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {
    Optional<PlanFeature> findByPlanName(String planName);
}
