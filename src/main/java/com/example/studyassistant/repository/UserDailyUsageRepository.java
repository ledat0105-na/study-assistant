package com.example.studyassistant.repository;

import com.example.studyassistant.entity.UserDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface UserDailyUsageRepository extends JpaRepository<UserDailyUsage, Long> {
    Optional<UserDailyUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
}
