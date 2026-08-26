package com.example.studyassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "user_daily_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "usage_date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDailyUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate = LocalDate.now();

    @Column(name = "chat_count", nullable = false)
    private Integer chatCount = 0;

    @Column(name = "quiz_count", nullable = false)
    private Integer quizCount = 0;

    @Column(name = "flashcard_count", nullable = false)
    private Integer flashcardCount = 0;
}
