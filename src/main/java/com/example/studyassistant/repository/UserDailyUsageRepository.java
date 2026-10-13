package com.example.studyassistant.repository;

import com.example.studyassistant.entity.UserDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface UserDailyUsageRepository extends JpaRepository<UserDailyUsage, Long> {
    Optional<UserDailyUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM UserDailyUsage u WHERE u.user.id = :userId AND u.usageDate = :usageDate")
    java.util.Optional<UserDailyUsage> findByUserIdAndUsageDateForUpdate(
        @org.springframework.data.repository.query.Param("userId") Long userId,
        @org.springframework.data.repository.query.Param("usageDate") java.time.LocalDate usageDate);
}
