package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Topic;
import com.example.studyassistant.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/topics")
@CrossOrigin(origins = "*")
public class TopicController {
    @Autowired
    private TopicRepository topicRepository;

    @GetMapping("/document/{docId}")
    public ResponseEntity<List<Topic>> getTopicsByDocument(@PathVariable Long docId) {
        // Return only top-level parent topics (children are automatically fetched via @JsonManagedReference)
        List<Topic> rootTopics = topicRepository.findByDocumentIdAndParentIsNull(docId);
        return ResponseEntity.ok(rootTopics);
    }
}
