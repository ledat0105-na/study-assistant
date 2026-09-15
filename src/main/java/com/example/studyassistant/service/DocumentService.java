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
public class DocumentService {
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

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        // Trích xuất nội dung text thật từ file để AI đọc được
        String extractedText;
        String extractionError = null;
        try {
            extractedText = extractText(filePath, format);
        } catch (Exception ex) {
            extractedText = null;
            extractionError = "Không thể trích xuất nội dung file: " + ex.getMessage();
        }

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
        document.setExtractedText(extractedText);
        if (extractionError != null) {
            document.setStatus("FAILED");
            document.setErrorMessage(extractionError);
        } else {
            document.setStatus("READY");
        }
        document.setRetryCount(0);
        document.setCreatedAt(LocalDateTime.now());

        Document savedDoc = documentServiceSave(document);

        generateKnowledgeMapTopics(savedDoc);

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
            doc.setStatus("READY");
            Document saved = documentRepository.save(doc);
            topicRepository.deleteAll(topicRepository.findByDocumentId(saved.getId()));
            generateKnowledgeMapTopics(saved);
            return saved;
        } catch (Exception ex) {
            doc.setStatus("FAILED");
            doc.setErrorMessage("Không thể trích xuất nội dung file: " + ex.getMessage());
        }

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
        switch (format) {
            case "PDF": {
                try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(filePath.toFile())) {
                    org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                    StringBuilder sb = new StringBuilder();
                    int totalPages = pdf.getNumberOfPages();
                    for (int page = 1; page <= totalPages; page++) {
                        stripper.setStartPage(page);
                        stripper.setEndPage(page);
                        sb.append("[Trang ").append(page).append("]\n")
                                .append(stripper.getText(pdf)).append("\n");
                    }
                    text = sb.toString();
                }
                break;
            }
            case "DOCX": {
                try (java.io.InputStream is = Files.newInputStream(filePath);
                     org.apache.poi.xwpf.usermodel.XWPFDocument docx = new org.apache.poi.xwpf.usermodel.XWPFDocument(is);
                     org.apache.poi.xwpf.extractor.XWPFWordExtractor extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(docx)) {
                    text = extractor.getText();
                }
                break;
            }
            case "PPTX": {
                try (java.io.InputStream is = Files.newInputStream(filePath);
                     org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(is)) {
                    StringBuilder sb = new StringBuilder();
                    int slideNo = 1;
                    for (org.apache.poi.xslf.usermodel.XSLFSlide slide : ppt.getSlides()) {
                        sb.append("[Slide ").append(slideNo++).append("]\n");
                        for (org.apache.poi.sl.usermodel.Shape<?, ?> shape : slide.getShapes()) {
                            if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape textShape) {
                                sb.append(textShape.getText()).append("\n");
                            }
                        }
                    }
                    text = sb.toString();
                }
                break;
            }
            default:
                throw new IOException("Định dạng không hỗ trợ trích xuất: " + format);
        }
        if (text == null) text = "";
        if (text.length() > MAX_EXTRACTED_CHARS) text = text.substring(0, MAX_EXTRACTED_CHARS);
        return text;
    }

    private int getDocumentQuotaByPlan(String plan) {
        if ("PREMIUM".equalsIgnoreCase(plan)) return 200;
        if ("BASIC".equalsIgnoreCase(plan)) return 50;
        return 10; // Gói FREE tối đa 10 tài liệu
    }

    private void generateKnowledgeMapTopics(Document doc) {
        String text = doc.getExtractedText();
        if (text == null || text.isBlank() || !aiService.hasApiKey()) {
            seedFallbackTopic(doc, "Chưa thể tạo sơ đồ tư duy tự động (thiếu nội dung trích xuất hoặc chưa cấu hình OpenAI API Key). Bạn vẫn có thể đọc trực tiếp tài liệu.");
            return;
        }

        String snippet = text.length() > 30000 ? text.substring(0, 30000) : text;
        String prompt = "Đọc kỹ nội dung tài liệu học tập dưới đây (đã đánh dấu ranh giới trang bằng [Trang N] hoặc [Slide N]) " +
                "và trả về CHỈ MỘT đối tượng JSON (không thêm chữ nào khác, không dùng markdown ```), có dạng:\n" +
                "{\"topics\": [{\"name\": \"Tên chương\", \"pageStart\": 1, \"pageEnd\": 5, \"description\": \"Tóm tắt nội dung chương này (2-3 câu)\", " +
                "\"children\": [{\"name\": \"Tên mục con\", \"pageStart\": 1, \"pageEnd\": 2, \"description\": \"Tóm tắt mục này (1-2 câu)\", " +
                "\"children\": [{\"name\": \"Một ý/khái niệm/thuật ngữ cụ thể trong mục này\", \"pageStart\": 1, \"pageEnd\": 1, \"description\": \"Giải thích ngắn 1 câu\", \"children\": []}]}]}]}\n\n" +
                "YÊU CẦU BẮT BUỘC để sơ đồ dễ hiểu (giống sơ đồ tư duy NotebookLM):\n" +
                "- Dùng ĐÚNG tên chương/mục thật có trong tài liệu, không tự bịa, không dịch sang ngôn ngữ khác.\n" +
                "- Cấu trúc tối đa 3 CẤP: chương (cấp 1) -> mục con (cấp 2) -> ý/khái niệm/thuật ngữ cụ thể (cấp 3, node lá).\n" +
                "- SỐ LƯỢNG NHÁNH Ở MỖI CẤP PHẢI DỰA THEO ĐỘ PHONG PHÚ THỰC TẾ CỦA TÀI LIỆU, không theo con số cố định: phần nào tài liệu trình bày nhiều khái niệm/ví dụ/định nghĩa thì tách nhiều nhánh con để người đọc dễ nắm ý; phần nào tài liệu chỉ nói sơ qua thì để ít nhánh hoặc không cần tách cấp 3. TUYỆT ĐỐI không thêm nhánh giả/lặp ý chỉ để cho đủ số lượng.\n" +
                "- Ưu tiên mục tiêu: người xem sơ đồ nắm được các khái niệm/thuật ngữ quan trọng thực sự có trong tài liệu, không bỏ sót ý chính nhưng cũng không vụn vặt hoá những chi tiết không quan trọng.\n" +
                "- Tối đa 10 chương chính. Tên node ngắn gọn (dưới 8 từ) nhưng phải cụ thể, không chung chung kiểu \"Nội dung chính\", \"Tổng quan\".\n" +
                "- pageStart/pageEnd lấy đúng từ nhãn [Trang N]/[Slide N] bao quanh nội dung đó trong tài liệu.\n\n" +
                "TÀI LIỆU:\n" + snippet;

        try {
            String raw = aiService.callOpenAI(prompt, true);
            String json = extractJsonObject(raw);
            JsonNode root = objectMapper.readTree(json);
            JsonNode topicsNode = root.get("topics");
            if (topicsNode == null || !topicsNode.isArray() || topicsNode.isEmpty()) {
                seedFallbackTopic(doc, "AI không trích xuất được cấu trúc chương rõ ràng từ tài liệu này.");
                return;
            }
            int order = 1;
            for (JsonNode node : topicsNode) {
                saveTopicNode(node, doc, null, order++);
            }
        } catch (Exception e) {
            seedFallbackTopic(doc, "Không thể tự động tạo sơ đồ tư duy: " + e.getMessage());
        }
    }

    private void saveTopicNode(JsonNode node, Document doc, Topic parent, int order) {
        Topic topic = new Topic();
        topic.setName(node.hasNonNull("name") ? node.get("name").asText() : "Chủ đề");
        topic.setDescription(node.hasNonNull("description") ? node.get("description").asText() : null);
        topic.setPageStart(node.hasNonNull("pageStart") ? node.get("pageStart").asInt() : 1);
        topic.setPageEnd(node.hasNonNull("pageEnd") ? node.get("pageEnd").asInt() : topic.getPageStart());
        topic.setSortOrder(order);
        topic.setDocument(doc);
        topic.setParent(parent);
        Topic saved = topicRepository.save(topic);

        JsonNode children = node.get("children");
        if (children != null && children.isArray()) {
            int childOrder = 1;
            for (JsonNode child : children) {
                saveTopicNode(child, doc, saved, childOrder++);
            }
        }
    }

    private void seedFallbackTopic(Document doc, String reason) {
        Topic topic = new Topic();
        topic.setName(doc.getFileName());
        topic.setDescription(reason);
        topic.setPageStart(1);
        topic.setPageEnd(doc.getTotalPages() != null ? doc.getTotalPages() : 1);
        topic.setSortOrder(1);
        topic.setDocument(doc);
        topicRepository.save(topic);
    }

    private String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalStateException("Phản hồi của AI không chứa JSON hợp lệ");
        }
        return raw.substring(start, end + 1);
    }
}
