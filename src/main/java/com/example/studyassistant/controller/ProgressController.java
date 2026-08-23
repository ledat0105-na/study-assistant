package com.example.studyassistant.controller;

import com.example.studyassistant.entity.StudyProgress;
import com.example.studyassistant.entity.Topic;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/progress")
@CrossOrigin(origins = "*")
public class ProgressController {
    @Autowired
    private StudyProgressRepository studyProgressRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private FlashcardRepository flashcardRepository;

    @Autowired
    private QuizResultRepository quizResultRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/dashboard/user/{userId}")
    public ResponseEntity<?> getDashboardStats(@PathVariable Long userId) {
        long totalDocs = documentRepository.count();
        long totalTopics = topicRepository.count();
        long totalFlashcards = flashcardRepository.count();
        
        List<StudyProgress> progressList = studyProgressRepository.findByUserId(userId);
        long completedTopics = progressList.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        long learningTopics = progressList.stream().filter(p -> "LEARNING".equals(p.getStatus())).count();

        // Calculate average quiz accuracy
        double avgAccuracy = quizResultRepository.findByUserId(userId).stream()
                .mapToDouble(r -> r.getScore())
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", totalDocs);
        stats.put("totalTopics", totalTopics);
        stats.put("totalFlashcards", totalFlashcards);
        stats.put("completedTopics", completedTopics);
        stats.put("learningTopics", learningTopics);
        stats.put("averageAccuracy", String.format("%.0f%%", avgAccuracy));
        stats.put("studyTime", "14.5 hrs"); // Mock study hours
        
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateTopicProgress(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        Long topicId = Long.valueOf(payload.get("topicId").toString());
        String status = payload.get("status").toString(); // "NOT_STARTED", "LEARNING", "COMPLETED"

        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Topic> topicOpt = topicRepository.findById(topicId);

        if (userOpt.isEmpty() || topicOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User or Topic not found"));
        }

        Optional<StudyProgress> progressOpt = studyProgressRepository.findByUserIdAndTopicId(userId, topicId);
        StudyProgress progress;
        if (progressOpt.isPresent()) {
            progress = progressOpt.get();
        } else {
            progress = new StudyProgress();
            progress.setUser(userOpt.get());
            progress.setTopic(topicOpt.get());
        }

        progress.setStatus(status);
        progress.setProgress("COMPLETED".equals(status) ? 100.0 : ("LEARNING".equals(status) ? 50.0 : 0.0));

        StudyProgress saved = studyProgressRepository.save(progress);
        return ResponseEntity.ok(saved);
    }
}
