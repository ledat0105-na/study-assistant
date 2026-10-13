package com.example.studyassistant.service;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.Notebook;
import com.example.studyassistant.entity.Topic;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.DocumentRepository;
import com.example.studyassistant.repository.NotebookRepository;
import com.example.studyassistant.repository.TopicRepository;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@lombok.extern.slf4j.Slf4j
public class DocumentService {
    @Autowired
    private com.example.studyassistant.retrieval.service.DocumentParsingService documentParsingService;

    @Autowired
    private com.example.studyassistant.retrieval.service.DocumentIndexingService documentIndexingService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private AiService aiService;

    @Autowired
    private QuotaService quotaService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Đường dẫn lưu file nằm ngoài public webroot để đảm bảo an toàn
    private final String UPLOAD_DIR = "storage_private/uploads";

    // DOC-01: Upload tài liệu
    public Document uploadDocument(Long userId, Long notebookId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        // 1. Validate file null/empty trước khi tính dung lượng
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file để upload");
        }

        // 2. Kiểm tra Quota số lượng tài liệu & dung lượng theo plan
        quotaService.checkStorageAndUploadQuota(userId, user.getPlan(), file.getSize());

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

        // Trích xuất nội dung text thật từ file để AI đọc được
        String extractedText;
        String extractionError = null;
        try {
            extractedText = extractText(filePath, format);
        } catch (Exception ex) {
            extractedText = null;
            extractionError = "Không thể trích xuất nội dung file: " + ex.getMessage();
        }

        // Parsing success starts PROCESSING; indexing activates the version before READY.
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
        document.setExtractedText(extractedText);
        if (extractionError != null) {
            document.setStatus("FAILED");
            document.setErrorMessage(extractionError);
        } else {
            document.setStatus("PROCESSING");
        }
        document.setRetryCount(0);
        document.setCreatedAt(LocalDateTime.now());

        Document savedDoc = documentServiceSave(document);

        // Index every parsed document, including uploads without a notebook.
        if ("PROCESSING".equals(savedDoc.getStatus())) {
            try {
                documentIndexingService.indexDocument(savedDoc.getId());
            } catch (Exception e) {
                // DocumentIndexingService handles setting status to FAILED and logging
            }
            savedDoc = documentRepository.findById(savedDoc.getId()).orElse(savedDoc);
        }

        return savedDoc;
    }

    // DOC-08: Gán/Cập nhật Notebook cho tài liệu và thực hiện RAG Indexing
    public Document assignNotebook(Long userId, Long documentId, Long notebookId) {
        Document doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));

        Notebook notebook = notebookRepository.findByIdAndUserIdAndIsDeletedFalse(notebookId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notebook không tồn tại hoặc bạn không có quyền truy cập"));

        if (!doc.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Tài liệu không thuộc về người dùng này");
        }

        doc.setNotebook(notebook);
        Document saved = documentRepository.save(doc);

        if (saved.getFilePath() != null && Files.exists(Paths.get(saved.getFilePath()))) {
            try {
                documentIndexingService.indexDocument(saved.getId());
            } catch (Exception e) {
                // DocumentIndexingService sets FAILED status on error
            }
        }

        return documentRepository.findById(saved.getId()).orElse(saved);
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

    public Document getDocumentById(Long userId, Long documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));
    }

    // DOC-03: Xem trạng thái xử lý tài liệu
    public Map<String, Object> getDocumentStatus(Long userId, Long documentId) {
        Document doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại hoặc bạn không có quyền truy cập"));

        Map<String, Object> response = new HashMap<>();
        response.put("id", doc.getId());
        response.put("status", doc.getStatus());
        response.put("activeIndexVersion", doc.getActiveIndexVersion());
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

        try {
            Path path = Paths.get(doc.getFilePath());
            String text = extractText(path, doc.getFileType());
            doc.setExtractedText(text);
            documentRepository.save(doc);
            try {
                documentIndexingService.indexDocument(doc.getId());
            } catch (Exception e) {}
            return documentRepository.findById(doc.getId()).orElse(doc);
        } catch (Exception ex) {
            doc.setStatus("FAILED");
            doc.setErrorMessage("Không thể trích xuất nội dung file: " + ex.getMessage());
        }

        return documentRepository.save(doc);
    }

    // Keep referenced learning history; hide the document and release its active index.
    @org.springframework.transaction.annotation.Transactional
    public void deleteDocument(Long userId, Long documentId) {
        Document doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại hoặc bạn không có quyền xóa"));

        doc = documentRepository.findByIdWithPessimisticLock(documentId)
                .filter(locked -> locked.getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại hoặc bạn không có quyền xóa"));
        String filePath = doc.getFilePath();
        String activeIndexVersion = doc.getActiveIndexVersion();
        doc.setStatus("DELETED");
        doc.setActiveIndexVersion(null);
        doc.setErrorMessage(null);
        documentRepository.saveAndFlush(doc);

        // External cleanup must not run if the database transaction rolls back.
        Runnable cleanup = () -> cleanupDeletedDocument(documentId, activeIndexVersion, filePath);
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { cleanup.run(); }
                    });
        } else {
            cleanup.run();
        }
    }

    private void cleanupDeletedDocument(Long documentId, String activeIndexVersion, String filePath) {
        if (activeIndexVersion != null && !activeIndexVersion.isBlank()) {
            try {
                documentIndexingService.cleanupVersion(documentId, activeIndexVersion);
            } catch (Exception e) {
                log.warn("Vector cleanup failed for deleted document {}", documentId, e);
            }
        }
        if (filePath != null && !filePath.isBlank()) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (Exception e) {
                log.warn("File cleanup failed for deleted document {}", documentId, e);
            }
        }
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

    // DOC-07: Lấy nội dung text đã trích xuất (dùng cho DOCX/PPTX ở trang đọc,
    // vì các định dạng này không thể render trực tiếp bằng PDF.js như PDF)
    public Document getDocumentContent(Long userId, Long documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài liệu không tồn tại hoặc bạn không có quyền xem"));
    }

    private boolean isSupportedContentType(String contentType, String format) {
        if ("PDF".equals(format) && contentType.contains("pdf")) return true;
        if ("DOCX".equals(format) && (contentType.contains("wordprocessingml") || contentType.contains("msword"))) return true;
        if ("PPTX".equals(format) && (contentType.contains("presentationml") || contentType.contains("powerpoint"))) return true;
        return false;
    }

    private static final int MAX_EXTRACTED_CHARS = 200_000;

    private String extractText(Path filePath, String format) throws IOException {
        String text;
        com.example.studyassistant.retrieval.model.ParsedDocument parsed = documentParsingService.parse(filePath, format);
        text = parsed.toBackwardCompatibleRawText();
        if (text == null) text = "";
        if (text.length() > MAX_EXTRACTED_CHARS) text = text.substring(0, MAX_EXTRACTED_CHARS);
        return text;
    }

    public int getDocumentQuotaByPlan(String plan) {
        return quotaService.getMaxDocuments(plan);
    }
}
