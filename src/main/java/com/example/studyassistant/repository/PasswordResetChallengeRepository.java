package com.example.studyassistant.repository;
import com.example.studyassistant.entity.PasswordResetChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallenge,Long> {}
