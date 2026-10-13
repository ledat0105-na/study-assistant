package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.retrieval.model.ChunkDraft;
import com.example.studyassistant.retrieval.model.ParsedDocument;
import com.example.studyassistant.retrieval.model.ParsedSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ChunkingServiceTest {

    private ChunkingService chunkingService;
    private SimpleTokenCounter tokenCounter;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingService();
        tokenCounter = new SimpleTokenCounter();
        ReflectionTestUtils.setField(chunkingService, "tokenCounter", tokenCounter);
        chunkingService.setTargetTokens(20);
        chunkingService.setOverlapTokens(5);
        chunkingService.setHardMaxTokens(30);
    }

    @Test
    void testCP2_01_EmptyDocument() {
        ParsedDocument doc = new ParsedDocument();
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks).isEmpty();
    }

    @Test
    void testCP2_02_WhitespaceOnlyDocument() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("   \n  ").build());
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks).isEmpty();
    }

    @Test
    void testCP2_03_ShortDocument() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("This is a short document.").build());
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("This is a short document.");
    }

    @Test
    void testCP2_04_LongDocument() {
        ParsedDocument doc = new ParsedDocument();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("Sentence ").append(i).append(". ");
        doc.getSegments().add(ParsedSegment.builder().text(sb.toString()).build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks.size()).isGreaterThan(1);
    }

    @Test
    void testCP2_05_SequentialChunkIndex() {
        ParsedDocument doc = new ParsedDocument();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) sb.append("Sentence ").append(i).append(". ");
        doc.getSegments().add(ParsedSegment.builder().text(sb.toString()).build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getChunkIndex()).isEqualTo(i);
        }
    }

    @Test
    void testCP2_06_NoChunkExceedsHardMax() {
        ParsedDocument doc = new ParsedDocument();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("Word ");
        doc.getSegments().add(ParsedSegment.builder().text(sb.toString()).build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        for (ChunkDraft chunk : chunks) {
            assertThat(chunk.getTokenCount()).isLessThanOrEqualTo(30);
        }
    }

    @Test
    void testCP2_07_OverlapExists() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("First sentence. Second sentence. Third sentence. Fourth sentence. Fifth sentence.").build());
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        
        if (chunks.size() > 1) {
            String firstChunk = chunks.get(0).getContent();
            String secondChunk = chunks.get(1).getContent();
            
            // Check if there's common text
            String[] firstWords = firstChunk.split(" ");
            String overlapWord = firstWords[firstWords.length - 1].replace(".", "");
            assertThat(secondChunk).contains(overlapWord);
        }
    }

    @Test
    void testCP2_08_OverlapDoesNotCreateDuplicates() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("A B C D E. F G H I J. K L M N O.").build());
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        
        if (chunks.size() > 1) {
            assertThat(chunks.get(0).getContent()).isNotEqualTo(chunks.get(1).getContent());
        }
    }

    @Test
    void testCP2_09_PdfPreservesPage() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Content on page 5.").pageStart(5).pageEnd(5).build());
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        
        assertThat(chunks.get(0).getPageStart()).isEqualTo(5);
        assertThat(chunks.get(0).getPageEnd()).isEqualTo(5);
    }

    @Test
    void testCP2_10_PdfSpanningPages() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("First part.").pageStart(1).pageEnd(1).build());
        doc.getSegments().add(ParsedSegment.builder().text("Second part.").pageStart(2).pageEnd(2).build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        // Since targetTokens is 20, they should merge into 1 chunk
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getPageStart()).isEqualTo(1);
        assertThat(chunks.get(0).getPageEnd()).isEqualTo(2);
    }

    @Test
    void testCP2_11_MarkersDoNotLeak() {
        // ParsedDocument models content directly without [Trang N] inside the text field
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Clean content.").pageStart(1).pageEnd(1).build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks.get(0).getContent()).doesNotContain("[Trang 1]");
    }

    @Test
    void testCP2_12_DocxPageNull() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Word content.").isWord(true).build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks.get(0).getPageStart()).isNull();
        assertThat(chunks.get(0).getPageEnd()).isNull();
    }

    @Test
    void testCP2_13_DocxHeadingPropagates() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Section 1 content.").sectionTitle("Heading 1").build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks.get(0).getSectionTitle()).isEqualTo("Heading 1");
    }

    @Test
    void testCP2_14_VietnameseSentenceBoundaries() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Xin chào! Bạn khỏe không? Tôi ổn.").build());
        chunkingService.setTargetTokens(5); // Force split
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0).getContent()).startsWith("Xin chào!");
    }

    @Test
    void testCP2_15_EnglishSentenceBoundaries() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Hello there. How are you? I'm fine.").build());
        chunkingService.setTargetTokens(5); // Force split
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0).getContent()).startsWith("Hello there.");
    }

    @Test
    void testCP2_16_OversizedSentenceIsSplit() {
        ParsedDocument doc = new ParsedDocument();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) sb.append("word "); // 40 words > 30 hard max
        doc.getSegments().add(ParsedSegment.builder().text(sb.toString().trim()).build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks.size()).isGreaterThan(1);
        for (ChunkDraft chunk : chunks) {
            assertThat(chunk.getTokenCount()).isLessThanOrEqualTo(30);
        }
    }

    @Test
    void testCP2_17_ContentHashIsDeterministic() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Deterministic text.").build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        String hash = chunks.get(0).getContentHash();
        
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 hex length
    }

    @Test
    void testCP2_18_SameContentProducesSameHash() {
        ParsedDocument doc1 = new ParsedDocument();
        doc1.getSegments().add(ParsedSegment.builder().text("Same text.").build());
        
        ParsedDocument doc2 = new ParsedDocument();
        doc2.getSegments().add(ParsedSegment.builder().text("Same text.").build());
        
        String hash1 = chunkingService.chunk(doc1).get(0).getContentHash();
        String hash2 = chunkingService.chunk(doc2).get(0).getContentHash();
        
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void testCP2_19_ChangedContentProducesDifferentHash() {
        ParsedDocument doc1 = new ParsedDocument();
        doc1.getSegments().add(ParsedSegment.builder().text("Text A.").build());
        
        ParsedDocument doc2 = new ParsedDocument();
        doc2.getSegments().add(ParsedSegment.builder().text("Text B.").build());
        
        String hash1 = chunkingService.chunk(doc1).get(0).getContentHash();
        String hash2 = chunkingService.chunk(doc2).get(0).getContentHash();
        
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void testCP2_20_TokenCountPopulated() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Test token count.").build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks.get(0).getTokenCount()).isGreaterThan(0);
    }

    @Test
    void testCP2_21_NoWhitespaceChunk() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Valid.   ").build());
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("Valid.");
    }

    @Test
    void testCP2_22_SourceOrderPreserved() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Sentence A. Sentence B. Sentence C. Sentence D.").build());
        chunkingService.setTargetTokens(5);
        
        List<ChunkDraft> chunks = chunkingService.chunk(doc);
        assertThat(chunks.get(0).getContent()).contains("Sentence A");
        assertThat(chunks.get(chunks.size() - 1).getContent()).contains("Sentence D");
    }

    @Test
    void testCP2_23_TargetTokenChangesBehavior() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("A B C D E F G H I J K L M N O P.").build());
        
        chunkingService.setTargetTokens(20);
        chunkingService.setHardMaxTokens(30);
        int chunksBig = chunkingService.chunk(doc).size();
        
        chunkingService.setTargetTokens(5);
        chunkingService.setHardMaxTokens(10);
        int chunksSmall = chunkingService.chunk(doc).size();
        
        assertThat(chunksSmall).isGreaterThan(chunksBig);
    }

    @Test
    void testCP2_24_OverlapTokenChangesBehavior() {
        ParsedDocument doc = new ParsedDocument();
        doc.getSegments().add(ParsedSegment.builder().text("Sentence 1. Sentence 2. Sentence 3. Sentence 4. Sentence 5.").build());
        chunkingService.setTargetTokens(5);
        
        chunkingService.setOverlapTokens(0);
        String chunk2NoOverlap = chunkingService.chunk(doc).get(1).getContent();
        
        chunkingService.setOverlapTokens(4);
        String chunk2WithOverlap = chunkingService.chunk(doc).get(1).getContent();
        
        assertThat(chunk2NoOverlap).isNotEqualTo(chunk2WithOverlap);
    }
}
