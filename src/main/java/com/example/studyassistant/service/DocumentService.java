package com.example.studyassistant.service;

import com.example.studyassistant.entity.Document;
import com.example.studyassistant.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {
    @Autowired
    private DocumentRepository documentRepository;

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    public Optional<Document> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    public Document saveDocument(Document document) {
        return documentRepository.save(document);
    }

    public boolean deleteDocument(Long id) {
        Optional<Document> docOpt = documentRepository.findById(id);
        if (docOpt.isPresent()) {
            Document doc = docOpt.get();
            // Delete actual file
            if (doc.getFilePath() != null) {
                try {
                    Path path = Paths.get(doc.getFilePath());
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    System.err.println("Failed to delete file: " + e.getMessage());
                }
            }
            documentRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
