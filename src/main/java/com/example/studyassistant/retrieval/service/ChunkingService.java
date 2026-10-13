package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.model.ChunkDraft;
import com.example.studyassistant.retrieval.model.ParsedDocument;
import com.example.studyassistant.retrieval.model.ParsedSegment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ChunkingService {

    @Autowired
    private TokenCounter tokenCounter;

    @Value("${rag.chunk.target-tokens:600}")
    @Setter
    private int targetTokens;

    @Value("${rag.chunk.overlap-tokens:80}")
    @Setter
    private int overlapTokens;
    
    @Value("${rag.chunk.hard-max-tokens:1000}")
    @Setter
    private int hardMaxTokens;

    public List<ChunkDraft> chunk(ParsedDocument document) {
        List<ChunkDraft> drafts = new ArrayList<>();
        if (document == null || document.getSegments().isEmpty()) {
            return drafts;
        }

        List<SentenceDraft> allSentences = new ArrayList<>();
        for (ParsedSegment segment : document.getSegments()) {
            if (segment.getText() == null || segment.getText().trim().isEmpty()) {
                continue;
            }
            List<SentenceDraft> sentences = splitIntoSentences(segment);
            allSentences.addAll(sentences);
        }

        if (allSentences.isEmpty()) {
            return drafts;
        }

        ChunkBuilder currentBuilder = new ChunkBuilder();
        int chunkIndex = 0;

        for (int i = 0; i < allSentences.size(); i++) {
            SentenceDraft sentence = allSentences.get(i);
            
            // Check if sentence itself is oversized
            if (sentence.tokens > hardMaxTokens) {
                // Split oversized sentence deterministically (by words)
                List<SentenceDraft> splitSentences = splitOversizedSentence(sentence);
                for (SentenceDraft subSentence : splitSentences) {
                    if (currentBuilder.getTokens() + subSentence.tokens > targetTokens && currentBuilder.getTokens() > 0) {
                        drafts.add(currentBuilder.build(chunkIndex++));
                        currentBuilder = createNextBuilder(currentBuilder);
                    }
                    currentBuilder.add(subSentence);
                }
                continue;
            }

            if (currentBuilder.getTokens() + sentence.tokens > targetTokens && currentBuilder.getTokens() > 0) {
                drafts.add(currentBuilder.build(chunkIndex++));
                currentBuilder = createNextBuilder(currentBuilder);
            }
            currentBuilder.add(sentence);
        }

        if (currentBuilder.getTokens() > 0) {
            drafts.add(currentBuilder.build(chunkIndex));
        }

        return drafts;
    }

    private List<SentenceDraft> splitIntoSentences(ParsedSegment segment) {
        List<SentenceDraft> result = new ArrayList<>();
        // Split by paragraphs first to preserve strong boundaries
        String[] paragraphs = segment.getText().split("\n\\s*\n");
        BreakIterator iterator = BreakIterator.getSentenceInstance(new Locale("vi", "VN"));

        for (String paragraph : paragraphs) {
            String p = paragraph.trim();
            if (p.isEmpty()) continue;

            iterator.setText(p);
            int start = iterator.first();
            for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
                String sentenceText = p.substring(start, end).trim();
                if (sentenceText.isEmpty()) continue;
                
                int tokens = tokenCounter.count(sentenceText);
                result.add(new SentenceDraft(sentenceText, segment.getPageStart(), segment.getPageEnd(), segment.getSectionTitle(), tokens));
            }
        }
        return result;
    }

    private List<SentenceDraft> splitOversizedSentence(SentenceDraft oversized) {
        List<SentenceDraft> result = new ArrayList<>();
        String[] words = oversized.text.split("\\s+");
        StringBuilder currentPart = new StringBuilder();
        int currentTokens = 0;

        for (String word : words) {
            int wordTokens = tokenCounter.count(word);
            if (currentTokens + wordTokens > targetTokens && currentTokens > 0) {
                result.add(new SentenceDraft(currentPart.toString().trim(), oversized.pageStart, oversized.pageEnd, oversized.sectionTitle, currentTokens));
                currentPart = new StringBuilder();
                currentTokens = 0;
            }
            currentPart.append(word).append(" ");
            currentTokens += wordTokens;
        }

        if (currentTokens > 0) {
            result.add(new SentenceDraft(currentPart.toString().trim(), oversized.pageStart, oversized.pageEnd, oversized.sectionTitle, currentTokens));
        }
        return result;
    }

    private ChunkBuilder createNextBuilder(ChunkBuilder previous) {
        ChunkBuilder next = new ChunkBuilder();
        int overlapAccumulated = 0;
        // Iterate backwards over previous sentences to fill overlap
        for (int i = previous.sentences.size() - 1; i >= 0; i--) {
            SentenceDraft s = previous.sentences.get(i);
            if (overlapAccumulated + s.tokens <= overlapTokens) {
                next.sentences.add(0, s);
                overlapAccumulated += s.tokens;
            } else {
                break;
            }
        }
        return next;
    }

    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Normalize line endings and trim
            String normalized = content.trim().replaceAll("\\r\\n", "\n");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @AllArgsConstructor
    private static class SentenceDraft {
        String text;
        Integer pageStart;
        Integer pageEnd;
        String sectionTitle;
        int tokens;
    }

    private class ChunkBuilder {
        List<SentenceDraft> sentences = new ArrayList<>();

        void add(SentenceDraft s) {
            sentences.add(s);
        }

        int getTokens() {
            return sentences.stream().mapToInt(s -> s.tokens).sum();
        }

        ChunkDraft build(int chunkIndex) {
            StringBuilder sb = new StringBuilder();
            Integer pageStart = null;
            Integer pageEnd = null;
            String sectionTitle = null;

            for (int i = 0; i < sentences.size(); i++) {
                SentenceDraft s = sentences.get(i);
                sb.append(s.text);
                if (i < sentences.size() - 1) {
                    sb.append(" ");
                }
                
                if (s.pageStart != null) {
                    if (pageStart == null || s.pageStart < pageStart) pageStart = s.pageStart;
                }
                if (s.pageEnd != null) {
                    if (pageEnd == null || s.pageEnd > pageEnd) pageEnd = s.pageEnd;
                }
                
                // Deterministic section title: Use the section of the first sentence of the chunk
                // that actually has a section title
                if (sectionTitle == null && s.sectionTitle != null) {
                    sectionTitle = s.sectionTitle;
                }
            }

            String content = sb.toString().trim();
            int totalTokens = tokenCounter.count(content); // Re-count exact total for the whole chunk

            return ChunkDraft.builder()
                    .chunkIndex(chunkIndex)
                    .content(content)
                    .pageStart(pageStart)
                    .pageEnd(pageEnd)
                    .sectionTitle(sectionTitle)
                    .tokenCount(totalTokens)
                    .contentHash(calculateHash(content))
                    .build();
        }
    }
}
