package com.example.studyassistant.service;

import com.example.studyassistant.entity.PlanFeature;
import com.example.studyassistant.entity.UserDailyUsage;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.PlanFeatureRepository;
import com.example.studyassistant.repository.UserDailyUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class QuotaService {

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @Autowired
    private UserDailyUsageRepository userDailyUsageRepository;

    @Autowired
    private DocumentRepository documentRepository;

    // Lấy cấu hình tính năng gói từ DB (Mặc định nếu chưa init DB)
    public PlanFeature getPlanFeature(String planName) {
        String name = (planName != null) ? planName.toUpperCase() : "FREE";
        return planFeatureRepository.findByPlanName(name)
                .orElseGet(() -> {
                    if ("PREMIUM".equals(name)) {
                        return new PlanFeature(null, "PREMIUM", 100, 500, 200, 500, 500, 10L * 1024 * 1024 * 1024, 3650);
                    } else if ("BASIC".equals(name)) {
                        return new PlanFeature(null, "BASIC", 20, 100, 50, 100, 100, 2L * 1024 * 1024 * 1024, 180);
                    } else {
                        return new PlanFeature(null, "FREE", 3, 10, 5, 10, 10, 100L * 1024 * 1024, 7);
                    }
                });
    }

    // Kiểm tra và tăng số lượt chat trong ngày
    public void checkAndIncrementChatLimit(Long userId, String userPlan) {
        PlanFeature feature = getPlanFeature(userPlan);
        UserDailyUsage usage = getOrCreateTodayUsage(userId);

        if (usage.getChatCount() >= feature.getDailyChatLimit()) {
            throw new IllegalStateException("Bạn đã đạt giới hạn " + feature.getDailyChatLimit() + " lượt Chat/ngày của gói " + userPlan + ". Vui lòng nâng cấp gói để tiếp tục.");
        }

        usage.setChatCount(usage.getChatCount() + 1);
        userDailyUsageRepository.save(usage);
    }

    // Kiểm tra và tăng số lượt Quiz trong ngày
    public void checkAndIncrementQuizLimit(Long userId, String userPlan) {
        PlanFeature feature = getPlanFeature(userPlan);
        UserDailyUsage usage = getOrCreateTodayUsage(userId);

        if (usage.getQuizCount() >= feature.getDailyQuizLimit()) {
            throw new IllegalStateException("Bạn đã đạt giới hạn " + feature.getDailyQuizLimit() + " lượt Quiz/ngày của gói " + userPlan + ".");
        }

        usage.setQuizCount(usage.getQuizCount() + 1);
        userDailyUsageRepository.save(usage);
    }

    // Kiểm tra và tăng số lượt Flashcard trong ngày
    public void checkAndIncrementFlashcardLimit(Long userId, String userPlan, int count) {
        PlanFeature feature = getPlanFeature(userPlan);
        UserDailyUsage usage = getOrCreateTodayUsage(userId);

        if (usage.getFlashcardCount() + count > feature.getDailyFlashcardLimit()) {
            throw new IllegalStateException("Thao tác này vượt quá giới hạn " + feature.getDailyFlashcardLimit() + " Flashcard/ngày của gói " + userPlan + ".");
        }

        usage.setFlashcardCount(usage.getFlashcardCount() + count);
        userDailyUsageRepository.save(usage);
    }

    // Kiểm tra giới hạn Upload/tháng và Dung lượng Storage
    public void checkStorageAndUploadQuota(Long userId, String userPlan, long newFileSize) {
        PlanFeature feature = getPlanFeature(userPlan);

        // 1. Số lượng upload
        long currentDocCount = documentRepository.countByUserId(userId);
        if (currentDocCount >= feature.getMonthlyUploadLimit()) {
            throw new IllegalStateException("Bạn đã đạt giới hạn " + feature.getMonthlyUploadLimit() + " tài liệu upload của gói " + userPlan + ".");
        }

        // 2. Tổng dung lượng đã dùng tính từ Server DB
        Long currentTotalBytes = documentRepository.findByUserId(userId).stream()
                .mapToLong(doc -> doc.getFileSize() != null ? doc.getFileSize() : 0L)
                .sum();

        if (currentTotalBytes + newFileSize > feature.getMaxStorageBytes()) {
            long maxMb = feature.getMaxStorageBytes() / (1024 * 1024);
            throw new IllegalStateException("Dung lượng lưu trữ của bạn đã vượt quá giới hạn " + maxMb + "MB của gói " + userPlan + ".");
        }
    }

    private UserDailyUsage getOrCreateTodayUsage(Long userId) {
        LocalDate today = LocalDate.now();
        return userDailyUsageRepository.findByUserIdAndUsageDate(userId, today)
                .orElseGet(() -> {
                    UserDailyUsage u = new UserDailyUsage();
                    u.setUser(new com.example.studyassistant.entity.User(userId, null, null, null, null, null, null, null, null, null, null, null));
                    u.setUsageDate(today);
                    u.setChatCount(0);
                    u.setQuizCount(0);
                    u.setFlashcardCount(0);
                    return userDailyUsageRepository.save(u);
                });
    }
}
