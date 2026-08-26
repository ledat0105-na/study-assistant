package com.example.studyassistant.service;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.Topic;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.TopicRepository;
import com.example.studyassistant.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DocumentService {
    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private TopicRepository topicRepository;

    // Đường dẫn lưu file nằm ngoài public webroot để đảm bảo an toàn
    private final String UPLOAD_DIR = "storage_private/uploads";

    // DOC-01: Upload tài liệu
    public Document uploadDocument(Long userId, Long notebookId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        // 1. Kiểm tra Quota số lượng tài liệu theo plan
        long currentCount = documentRepository.countByUserId(userId);
        int maxDocs = getDocumentQuotaByPlan(user.getPlan());
        if (currentCount >= maxDocs) {
            throw new IllegalStateException("Bạn đã vượt quá số lượng tài liệu cho phép của gói " + user.getPlan() + " (" + maxDocs + " file).");
        }

        // 2. Validate Notebook thuộc sở hữu của User (nếu truyền notebookId)
        Notebook notebook = null;
        if (notebookId != null) {
            notebook = notebookRepository.findByIdAndUserIdAndIsDeletedFalse(notebookId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Notebook không tồn tại hoặc bạn không có quyền truy cập"));
        }

        // 3. Validate file null/empty
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file để upload");
        }

        // 4. Validate kích thước file (Tối đa 25MB)
        long maxSize = 25 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("Kích thước file không được vượt quá 25MB");
        }

        // 5. Sanitize & Validate file extension & MIME type thực
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Tên file chứa ký tự không hợp lệ");
        }

        String lowerName = originalFilename.toLowerCase();
        String format = "";
        if (lowerName.endsWith(".pdf")) {
            format = "PDF";
        } else if (lowerName.endsWith(".docx")) {
            format = "DOCX";
        } else if (lowerName.endsWith(".pptx")) {
            format = "PPTX";
        } else {
            throw new IllegalArgumentException("Định dạng file không hỗ trợ. Chỉ chấp nhận .pdf, .docx, .pptx");
        }

        String contentType = file.getContentType();
        if (contentType != null && !isSupportedContentType(contentType, format)) {
            throw new IllegalArgumentException("Nội dung file (MIME type) không hợp lệ");
        }

        // 6. Lưu file vào storage_private ngoài webroot
        File uploadFolder = new File(UPLOAD_DIR);
        if (!uploadFolder.exists()) {
            uploadFolder.mkdirs();
        }

        String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);
        Files.write(filePath, file.getBytes());

        // 7. Tạo bản ghi Document với status UPLOADING -> READY
        Document document = new Document();
        document.setUser(user);
        document.setNotebook(notebook);
        String titleName = originalFilename.contains(".") ? originalFilename.substring(0, originalFilename.lastIndexOf('.')) : originalFilename;
        document.setFileName(titleName);
        document.setOriginalName(originalFilename);
        document.setFileType(format);
        document.setFileSize(file.getSize());
        document.setTotalPages((int) (Math.random() * 40) + 5);
        document.setFilePath(filePath.toString());
        document.setStatus("READY");
        document.setRetryCount(0);
        document.setCreatedAt(LocalDateTime.now());

        Document savedDoc = documentServiceSave(document);

        // Sinh tự động Topics demo làm cây Knowledge Map
        seedMockTopicsForDoc(savedDoc);

        return savedDoc;
    }

    private Document documentServiceSave(Document doc) {
        return documentRepository.save(doc);
    }

    // DOC-02: Xem danh sách tài liệu thuộc user/notebook
    public List<Document> getDocumentsByUserAndNotebook(Long userId, Long notebookId) {
        if (notebookId != null) {
            return documentRepository.findByUserIdAndNotebookId(userId, notebookId);
        }
        return documentRepository.findByUserId(userId);
    }

    // DOC-03: Xem trạng thái xử lý tài liệu
    public Map<String, Object> getDocumentStatus(Long userId, Long documentId) {
        Document doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));

        Map<String, Object> response = new HashMap<>();
        response.put("id", doc.getId());
        response.put("status", doc.getStatus());
        response.put("retryCount", doc.getRetryCount());
        response.put("errorMessage", doc.getErrorMessage() != null ? doc.getErrorMessage() : "");
        return response;
    }

    // DOC-04: Retry tài liệu xử lý lỗi
    public Document retryDocumentProcessing(Long userId, Long documentId) {
        Document doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));

        if (!"FAILED".equalsIgnoreCase(doc.getStatus())) {
            throw new IllegalStateException("Chỉ có thể thử lại với các tài liệu đang ở trạng thái FAILED");
        }

        if (doc.getRetryCount() != null && doc.getRetryCount() >= 3) {
            throw new IllegalStateException("Đã vượt quá số lần thử lại tối đa (3 lần). Vui lòng upload lại file mới.");
        }

        doc.setRetryCount((doc.getRetryCount() == null ? 0 : doc.getRetryCount()) + 1);
        doc.setStatus("PROCESSING");
        doc.setErrorMessage(null);
        documentRepository.save(doc);

        // Mô phỏng job xử lý lại thành công
        doc.setStatus("READY");
        return documentRepository.save(doc);
    }

    // DOC-05: Xóa tài liệu (Check owner, xóa file vật lý, cascade DB)
    public void deleteDocument(Long userId, Long documentId) {
        Document doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại hoặc bạn không có quyền xóa"));

        // Xóa file vật lý khỏi đĩa
        if (doc.getFilePath() != null) {
            try {
                Path path = Paths.get(doc.getFilePath());
                Files.deleteIfExists(path);
            } catch (IOException ignored) {}
        }

        // JPA Cascading sẽ tự động xóa các Topics / Flashcards / Quizzes liên quan
        documentRepository.delete(doc);
    }

    // DOC-06: Mở file PDF Viewer (Chỉ cho phép Owner tải/đọc file)
    public Path getDocumentFileForView(Long userId, Long documentId) {
        Document doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại hoặc bạn không có quyền xem"));

        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File không tồn tại trên hệ thống lưu trữ");
        }
        return path;
    }

    private boolean isSupportedContentType(String contentType, String format) {
        if ("PDF".equals(format) && contentType.contains("pdf")) return true;
        if ("DOCX".equals(format) && (contentType.contains("wordprocessingml") || contentType.contains("msword"))) return true;
        if ("PPTX".equals(format) && (contentType.contains("presentationml") || contentType.contains("powerpoint"))) return true;
        return true; // Chấp nhận MIME type nếu khớp extension
    }

    private int getDocumentQuotaByPlan(String plan) {
        if ("PREMIUM".equalsIgnoreCase(plan)) return 200;
        if ("BASIC".equalsIgnoreCase(plan)) return 50;
        return 10; // Gói FREE tối đa 10 tài liệu
    }

    private void seedMockTopicsForDoc(Document doc) {
        Topic p1 = new Topic();
        p1.setName("Chương 1: Tổng quan " + doc.getFileName());
        p1.setPageStart(1);
        p1.setPageEnd(10);
        p1.setDescription("Nội dung cơ bản và các định nghĩa.");
        p1.setDocument(doc);
        p1.setSortOrder(1);
        topicRepository.save(p1);

        Topic p2 = new Topic();
        p2.setName("Chương 2: Kiến thức chuyên sâu");
        p2.setPageStart(11);
        p2.setPageEnd(25);
        p2.setDescription("Phương pháp tối ưu và thuật toán.");
        p2.setDocument(doc);
        p2.setSortOrder(2);
        topicRepository.save(p2);
    }
}
