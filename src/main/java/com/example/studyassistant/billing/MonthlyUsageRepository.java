package com.example.studyassistant.billing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
public interface MonthlyUsageRepository extends JpaRepository<MonthlyUsage,Long> {
 Optional<MonthlyUsage> findByUserIdAndPeriodStartAndFeature(Long userId,LocalDate period,String feature);
 @org.springframework.data.jpa.repository.Modifying(clearAutomatically=true,flushAutomatically=true)
 @org.springframework.data.jpa.repository.Query("UPDATE MonthlyUsage u SET u.used=u.used-1 WHERE u.userId=:userId AND u.periodStart=:period AND u.feature=:feature AND u.used>0")
 int refundOne(@org.springframework.data.repository.query.Param("userId") Long userId,
               @org.springframework.data.repository.query.Param("period") LocalDate period,
               @org.springframework.data.repository.query.Param("feature") String feature);
 List<MonthlyUsage> findByUserIdAndPeriodStart(Long userId,LocalDate period);
}
