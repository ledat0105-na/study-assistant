package com.example.studyassistant.controller;

import com.example.studyassistant.entity.Flashcard;
import com.example.studyassistant.repository.FlashcardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/flashcards")
@CrossOrigin(origins = "*")
public class FlashcardController {
    @Autowired
    private FlashcardRepository flashcardRepository;

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<List<Flashcard>> getFlashcardsByTopic(@PathVariable Long topicId) {
        return ResponseEntity.ok(flashcardRepository.findByTopicId(topicId));
    }

    @PostMapping
    public ResponseEntity<Flashcard> createFlashcard(@RequestBody Flashcard flashcard) {
        return ResponseEntity.ok(flashcardRepository.save(flashcard));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Flashcard> updateFlashcard(@PathVariable Long id, @RequestBody Flashcard details) {
        Optional<Flashcard> cardOpt = flashcardRepository.findById(id);
        if (cardOpt.isPresent()) {
            Flashcard card = cardOpt.get();
            card.setQuestion(details.getQuestion());
            card.setAnswer(details.getAnswer());
            return ResponseEntity.ok(flashcardRepository.save(card));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFlashcard(@PathVariable Long id) {
        if (flashcardRepository.existsById(id)) {
            flashcardRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
