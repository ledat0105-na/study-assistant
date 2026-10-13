package com.example.studyassistant.service;

import com.example.studyassistant.entity.PlanFeature;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.entity.UserDailyUsage;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.PlanFeatureRepository;
import com.example.studyassistant.repository.UserDailyUsageRepository;
import com.example.studyassistant.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    // Lấy cấu hình tính năng gói từ DB (Mặc định nếu chưa init DB)
    public PlanFeature getPlanFeature(String planName) {
        String name = (planName != null) ? planName.toUpperCase() : "FREE";
        if ("STUDENT".equals(name)) name = "BASIC";
        if ("PRO".equals(name)) name = "PREMIUM";
        final String searchName = name;
        return planFeatureRepository.findByPlanName(searchName)
                .orElseGet(() -> {
                    if ("PREMIUM".equals(searchName)) {
                        return new PlanFeature(null, "PREMIUM", 100, 500, 200, 500, 9999, 10L * 1024 * 1024 * 1024, 3650,
                            null, null, null, null);
                    } else if ("BASIC".equals(searchName)) {
                        return new PlanFeature(null, "BASIC", 20, 100, 50, 100, 50, 2L * 1024 * 1024 * 1024, 180,
                            null, null, null, null);
                    } else {
                        return new PlanFeature(null, "FREE", 3, 10, 5, 10, 3, 100L * 1024 * 1024, 7,
                            null, null, null, null);
                    }
                });
    }

    public int getMaxDocuments(String userPlan) {
        return getPlanFeature(userPlan).getMonthlyUploadLimit();
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

    // Kiểm tra và tăng số lượt tạo Presentation Outline trong ngày
    // MUST be called under User PESSIMISTIC_WRITE lock.
    public void checkAndIncrementPresentationOutlineLimit(Long userId, String userPlan) {
        PlanFeature feature = getPlanFeature(userPlan);
        UserDailyUsage usage = getOrCreateUsageForDate(userId, LocalDate.now(clock));

        Integer limit = feature.getDailyPresentationOutlineLimit();
        if (limit != null && usage.getPresentationOutlineCount() >= limit) {
            throw new IllegalStateException(
                "Daily presentation outline generation limit (" + limit + ") reached for plan " + userPlan + ".");
        }

        usage.setPresentationOutlineCount(usage.getPresentationOutlineCount() + 1);
        userDailyUsageRepository.save(usage);
    }

    /**
     * Reserve presentationSlideCount units for a new slide generation job.
     * MUST be called under User PESSIMISTIC_WRITE lock (serialization root).
     * Uses findByUserIdAndUsageDateForUpdate for the usage row.
     *
     * @param userId       authenticated user
     * @param usageDate    the date for which to reserve (from clock at TX time)
     * @param userPlan     plan name for limit lookup
     * @param count        number of slides to reserve
     */
    public void checkAndReserveSlideQuota(Long userId, LocalDate usageDate, String userPlan, int count) {
        if (count <= 0) return; // zero-work job, nothing to reserve
        PlanFeature feature = getPlanFeature(userPlan);
        UserDailyUsage usage = getOrCreateUsageForDate(userId, usageDate);

        Integer limit = feature.getDailyPresentationSlideLimit();
        if (limit != null && usage.getPresentationSlideCount() + count > limit) {
            throw new IllegalStateException(
                "Daily presentation slide generation limit (" + limit + ") would be exceeded. " +
                "Current: " + usage.getPresentationSlideCount() + ", requested: " + count + ", limit: " + limit + ".");
        }

        usage.setPresentationSlideCount(usage.getPresentationSlideCount() + count);
        userDailyUsageRepository.save(usage);
    }

    /**
     * Release unused slide quota reservation.
     * MUST be called under User PESSIMISTIC_WRITE lock.
     * Targets the EXACT reservationDate row (NOT today) to handle midnight crossings.
     * Throws invariant exception if release would make count negative (indicates a bug).
     *
     * @param userId          user
     * @param reservationDate the date on which the reservation was made
     * @param releaseCount    number of units to release (>= 0)
     */
    public void releaseSlideReservation(Long userId, LocalDate reservationDate, int releaseCount) {
        if (releaseCount <= 0) return;
        // Use the locked read to prevent concurrent release races
        Optional<UserDailyUsage> usageOpt = userDailyUsageRepository
            .findByUserIdAndUsageDateForUpdate(userId, reservationDate);
        if (usageOpt.isEmpty()) {
            // Row does not exist for that date — this is an invariant violation
            throw new IllegalStateException(
                "Cannot release slide reservation: no UserDailyUsage row found for userId=" + userId +
                " date=" + reservationDate);
        }
        UserDailyUsage usage = usageOpt.get();
        int newCount = usage.getPresentationSlideCount() - releaseCount;
        if (newCount < 0) {
            throw new IllegalStateException(
                "Slide quota invariant violation: release of " + releaseCount +
                " would make presentationSlideCount negative (current=" + usage.getPresentationSlideCount() + ").");
        }
        usage.setPresentationSlideCount(newCount);
        userDailyUsageRepository.save(usage);
    }

    // --- Internal helpers ---

    private UserDailyUsage getOrCreateTodayUsage(Long userId) {
        return getOrCreateUsageForDate(userId, LocalDate.now(clock));
    }

    /**
     * Find or create usage row for the given date.
     * Under User PESSIMISTIC_WRITE lock, the unique constraint on (user_id, usage_date)
     * ensures exactly-once creation even for cold-start.
     */
    private UserDailyUsage getOrCreateUsageForDate(Long userId, LocalDate date) {
        return userDailyUsageRepository.findByUserIdAndUsageDate(userId, date)
                .orElseGet(() -> {
                    UserDailyUsage u = new UserDailyUsage();
                    u.setUser(userRepository.getReferenceById(userId));
                    u.setUsageDate(date);
                    u.setChatCount(0);
                    u.setQuizCount(0);
                    u.setFlashcardCount(0);
                    u.setPresentationOutlineCount(0);
                    u.setPresentationSlideCount(0);
                    return userDailyUsageRepository.save(u);
                });
    }
}
