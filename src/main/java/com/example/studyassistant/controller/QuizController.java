package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Quiz;
import com.example.studyassistant.entity.QuizQuestion;
import com.example.studyassistant.entity.QuizResult;
import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.QuizQuestionRepository;
import com.example.studyassistant.repository.QuizRepository;
import com.example.studyassistant.repository.QuizResultRepository;
import com.example.studyassistant.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/quizzes")
@CrossOrigin(origins = "*")
public class QuizController {
    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private QuizResultRepository quizResultRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<List<Quiz>> getQuizzesByTopic(@PathVariable Long topicId) {
        return ResponseEntity.ok(quizRepository.findByTopicId(topicId));
    }

    @GetMapping("/{quizId}/questions")
    public ResponseEntity<List<QuizQuestion>> getQuestionsByQuiz(@PathVariable Long quizId) {
        return ResponseEntity.ok(quizQuestionRepository.findByQuizId(quizId));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitQuizAnswers(@RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        Long quizId = Long.valueOf(payload.get("quizId").toString());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = (Map<String, Object>) payload.get("answers");

        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);

        if (userOpt.isEmpty() || quizOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User or Quiz not found"));
        }

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizId(quizId);
        int correctAnswers = 0;
        for (QuizQuestion q : questions) {
            Object submittedAnswerObj = answers.get(q.getId().toString());
            if (submittedAnswerObj != null) {
                String subAnswerStr = "";
                if (submittedAnswerObj instanceof Integer) {
                    subAnswerStr = String.valueOf((char) ('A' + (Integer) submittedAnswerObj));
                } else {
                    subAnswerStr = submittedAnswerObj.toString().trim().toUpperCase();
                }

                if (subAnswerStr.equalsIgnoreCase(q.getCorrectAnswer().trim())) {
                    correctAnswers++;
                }
            }
        }

        QuizResult result = new QuizResult();
        result.setCorrectAnswers(correctAnswers);
        result.setTotalQuestions(questions.size());
        result.setScore(questions.isEmpty() ? 0.0 : ((double) correctAnswers / questions.size()) * 100.0);
        result.setUser(userOpt.get());
        result.setQuiz(quizOpt.get());

        QuizResult savedResult = quizResultRepository.save(result);
        return ResponseEntity.ok(savedResult);
    }

    @GetMapping("/history/user/{userId}")
    public ResponseEntity<List<QuizResult>> getQuizHistory(@PathVariable Long userId) {
        return ResponseEntity.ok(quizResultRepository.findByUserId(userId));
    }
}
