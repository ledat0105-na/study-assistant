package com.example.studyassistant.repository;

import com.example.studyassistant.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.id = :id")
    java.util.Optional<Payment> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    long countByStatus(String status);
    java.util.Optional<Payment> findFirstByUserIdOrderByIdDesc(Long userId);
    java.util.Optional<Payment> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    List<Payment> findByStatus(String status);
    List<Payment> findByUserId(Long userId);
}
