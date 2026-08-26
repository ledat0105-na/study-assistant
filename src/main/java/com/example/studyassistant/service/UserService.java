package com.example.studyassistant.service;

import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.util.HashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    // ACC-01: Đăng ký tài khoản (BCrypt, role STUDENT, plan FREE)
    public User register(User reqUser) {
        if (reqUser.getUsername() == null || reqUser.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username không được để trống");
        }
        if (reqUser.getEmail() == null || reqUser.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        if (reqUser.getPassword() == null || reqUser.getPassword().length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự");
        }
        if (userRepository.findByUsername(reqUser.getUsername().trim()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.findByEmail(reqUser.getEmail().trim()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User();
        user.setFullName(reqUser.getFullName());
        user.setUsername(reqUser.getUsername().trim());
        user.setEmail(reqUser.getEmail().trim());
        user.setPassword(HashUtil.hash(reqUser.getPassword()));
        
        // Buộc mặc định role=STUDENT và plan=FREE để ngăn privilege escalation
        user.setRole("STUDENT");
        user.setPlan("FREE");
        user.setStatus("ACTIVE");
        user.setFailedLoginAttempts(0);

        return userRepository.save(user);
    }

    // ACC-02: Đăng nhập (Email/Username + Password, check ACTIVE/LOCKED/SUSPENDED & Rate limit)
    public User login(String usernameOrEmail, String password) {
        if (usernameOrEmail == null || password == null) {
            throw new IllegalArgumentException("Username/email và mật khẩu là bắt buộc");
        }

        Optional<User> userOpt = userRepository.findByUsername(usernameOrEmail.trim());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(usernameOrEmail.trim());
        }

        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Tài khoản hoặc mật khẩu không chính xác");
        }

        User user = userOpt.get();

        // Kiểm tra trạng thái khóa tài khoản tạm thời
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Tài khoản đang bị tạm khóa do nhập sai nhiều lần. Phải đợi đến " + user.getLockUntil());
        }

        if ("LOCKED".equalsIgnoreCase(user.getStatus()) || "SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalStateException("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động. Vui lòng liên hệ quản trị viên.");
        }

        if (HashUtil.verify(password, user.getPassword())) {
            // Reset số lần đăng nhập sai
            user.setFailedLoginAttempts(0);
            user.setLockUntil(null);

            // Auto-upgrade mật khẩu legacy sang BCrypt nếu cần
            if (!user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$")) {
                user.setPassword(HashUtil.hash(password));
            }
            return userRepository.save(user);
        } else {
            // Tăng số lần đăng nhập thất bại (Rate limiting / Brute force prevention)
            int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 5) {
                user.setLockUntil(LocalDateTime.now().plusMinutes(15)); // Khóa 15 phút
            }
            userRepository.save(user);
            throw new IllegalArgumentException("Tài khoản hoặc mật khẩu không chính xác");
        }
    }

    // ACC-04: Quên mật khẩu & Đặt lại mật khẩu (Token 1 lần, expiry, phản hồi chung tránh enumeration)
    public String generatePasswordResetToken(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email != null ? email.trim() : "");
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30)); // 30 phút hết hạn
            userRepository.save(user);
            return token;
        }
        // Luôn trả về phản hồi thành công chung để chống Account Enumeration
        return null;
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token đặt lại mật khẩu không hợp lệ");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Mật khẩu mới phải từ 6 ký tự trở lên");
        }

        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token không hợp lệ hoặc đã hết hạn"));

        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token đặt lại mật khẩu đã hết hạn");
        }

        user.setPassword(HashUtil.hash(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    // ACC-05: Cập nhật hồ sơ cá nhân (Không cho sửa id, role, plan, status)
    public User updateProfile(Long userId, User profileDto) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        if (profileDto.getFullName() != null) {
            existingUser.setFullName(profileDto.getFullName().trim());
        }
        
        // Ngăn ngừa Privilege Escalation: Giữ nguyên role, plan, status
        return userRepository.save(existingUser);
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
    }

    // NB-01: Tạo Notebook (Kiểm tra Quota theo Plan)
    public Notebook createNotebook(Long userId, Notebook notebookDto) {
        User user = getUserById(userId);
        if (notebookDto.getTitle() == null || notebookDto.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên Notebook không được để trống");
        }
        if (notebookDto.getTitle().length() > 100) {
            throw new IllegalArgumentException("Tên Notebook không được vượt quá 100 ký tự");
        }

        long existingCount = notebookRepository.countByUserIdAndIsDeletedFalse(userId);
        int quota = getNotebookQuotaByPlan(user.getPlan());
        if (existingCount >= quota) {
            throw new IllegalStateException("Bạn đã đạt giới hạn Notebook cho gói " + user.getPlan() + " (" + quota + " notebook). Vui lòng nâng cấp gói dịch vụ.");
        }

        Notebook notebook = new Notebook();
        notebook.setTitle(notebookDto.getTitle().trim());
        notebook.setDescription(notebookDto.getDescription());
        notebook.setUser(user);
        notebook.setIsDeleted(false);
        notebook.setCreatedAt(LocalDateTime.now());
        notebook.setUpdatedAt(LocalDateTime.now());

        return notebookRepository.save(notebook);
    }

    // NB-02: Xem danh sách Notebook của currentUser (Query theo authenticatedUserId)
    public List<Notebook> getMyNotebooks(Long userId) {
        return notebookRepository.findByUserIdAndIsDeletedFalse(userId);
    }

    // NB-03: Đổi tên Notebook (Chỉ owner, validate title)
    public Notebook updateNotebook(Long userId, Long notebookId, Notebook updateDto) {
        Notebook notebook = notebookRepository.findByIdAndUserIdAndIsDeletedFalse(notebookId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notebook không tồn tại hoặc bạn không có quyền chỉnh sửa"));

        if (updateDto.getTitle() == null || updateDto.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên Notebook không được để trống");
        }
        if (updateDto.getTitle().length() > 100) {
            throw new IllegalArgumentException("Tên Notebook không được vượt quá 100 ký tự");
        }

        notebook.setTitle(updateDto.getTitle().trim());
        if (updateDto.getDescription() != null) {
            notebook.setDescription(updateDto.getDescription());
        }
        notebook.setUpdatedAt(LocalDateTime.now());

        return notebookRepository.save(notebook);
    }

    // NB-04: Xóa Notebook (Soft delete, kiểm tra owner)
    public void deleteNotebook(Long userId, Long notebookId) {
        Notebook notebook = notebookRepository.findByIdAndUserIdAndIsDeletedFalse(notebookId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notebook không tồn tại hoặc bạn không có quyền xóa"));

        notebook.setIsDeleted(true);
        notebook.setUpdatedAt(LocalDateTime.now());
        notebookRepository.save(notebook);
    }

    // NB-05: Mở Notebook (Xác minh owner)
    public Notebook getNotebookDetail(Long userId, Long notebookId) {
        return notebookRepository.findByIdAndUserIdAndIsDeletedFalse(notebookId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notebook không tồn tại hoặc bạn không có quyền truy cập"));
    }

    private int getNotebookQuotaByPlan(String plan) {
        if ("PREMIUM".equalsIgnoreCase(plan)) return 100;
        if ("BASIC".equalsIgnoreCase(plan)) return 20;
        return 5; // FREE plan mặc định tối đa 5 Notebook
    }
}
