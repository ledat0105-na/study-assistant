package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.entity.Topic;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.service.DocumentService;
import com.example.studyassistant.repository.TopicRepository;
import com.example.studyassistant.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class DocumentController {
    @Autowired
    private DocumentService documentService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    private final String UPLOAD_DIR = "uploads";

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file name"));
        }

        String format = "";
        if (originalFilename.toLowerCase().endsWith(".pdf")) {
            format = "PDF";
        } else if (originalFilename.toLowerCase().endsWith(".docx")) {
            format = "DOCX";
        } else if (originalFilename.toLowerCase().endsWith(".pptx")) {
            format = "PPTX";
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported file format. Please upload PDF, DOCX, or PPTX."));
        }

        try {
            // Get or create a default user to satisfy the foreign key constraint
            User defaultUser = userRepository.findAll().stream().findFirst().orElseGet(() -> {
                User u = new User();
                u.setFullName("Default Student");
                u.setUsername("student");
                u.setEmail("student@example.com");
                u.setPassword("password");
                u.setRole("STUDENT");
                return userRepository.save(u);
            });

            // Create uploads folder if not exists
            File uploadFolder = new File(UPLOAD_DIR);
            if (!uploadFolder.exists()) {
                uploadFolder.mkdirs();
            }

            String fileName = System.currentTimeMillis() + "_" + originalFilename;
            Path filePath = Paths.get(UPLOAD_DIR, fileName);
            Files.write(filePath, file.getBytes());

            // Create Document metadata
            Document document = new Document();
            document.setUser(defaultUser);
            document.setFileName(originalFilename.substring(0, originalFilename.lastIndexOf('.')));
            document.setOriginalName(originalFilename);
            document.setFileType(format);
            document.setFileSize(file.getSize());
            document.setTotalPages((int) (Math.random() * 50) + 10); // Mock page counts
            document.setFilePath(filePath.toString());
            document.setStatus("READY");

            Document savedDoc = documentService.saveDocument(document);

            // Automatically seed mock topics for the Knowledge Map using setter methods
            Topic parent1 = new Topic();
            parent1.setName("Chapter 1: Foundations of " + savedDoc.getFileName());
            parent1.setPageStart(1);
            parent1.setPageEnd(10);
            parent1.setDescription("Base foundations and core definitions.");
            parent1.setDocument(savedDoc);
            parent1.setSortOrder(1);
            parent1 = topicRepository.save(parent1);

            Topic parent2 = new Topic();
            parent2.setName("Chapter 2: Optimization Methods");
            parent2.setPageStart(11);
            parent2.setPageEnd(25);
            parent2.setDescription("Detailed calculation rules and backpropagation algorithms.");
            parent2.setDocument(savedDoc);
            parent2.setSortOrder(2);
            parent2 = topicRepository.save(parent2);

            Topic child1 = new Topic();
            child1.setName("Concept Model Specifications");
            child1.setPageStart(2);
            child1.setPageEnd(4);
            child1.setDescription("Comparison and modeling bounds.");
            child1.setDocument(savedDoc);
            child1.setParent(parent1);
            child1.setSortOrder(1);
            topicRepository.save(child1);

            Topic child2 = new Topic();
            child2.setName("Single Perceptron Calculations");
            child2.setPageStart(5);
            child2.setPageEnd(10);
            child2.setDescription("Single-layer boundaries.");
            child2.setDocument(savedDoc);
            child2.setParent(parent1);
            child2.setSortOrder(2);
            topicRepository.save(child2);

            Topic child3 = new Topic();
            child3.setName("Gradient Optimization Steps");
            child3.setPageStart(12);
            child3.setPageEnd(18);
            child3.setDescription("Weight gradient minimizers.");
            child3.setDocument(savedDoc);
            child3.setParent(parent2);
            child3.setSortOrder(1);
            topicRepository.save(child3);

            Topic child4 = new Topic();
            child4.setName("Backpropagation Algorithms");
            child4.setPageStart(19);
            child4.setPageEnd(25);
            child4.setDescription("Chain differentiation rules.");
            child4.setDocument(savedDoc);
            child4.setParent(parent2);
            child4.setSortOrder(2);
            topicRepository.save(child4);

            return ResponseEntity.ok(savedDoc);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save file: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Document>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDocumentById(@PathVariable Long id) {
        Optional<Document> docOpt = documentService.getDocumentById(id);
        if (docOpt.isPresent()) {
            return ResponseEntity.ok(docOpt.get());
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long id) {
        boolean deleted = documentService.deleteDocument(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
        }
        return ResponseEntity.notFound().build();
    }
}
