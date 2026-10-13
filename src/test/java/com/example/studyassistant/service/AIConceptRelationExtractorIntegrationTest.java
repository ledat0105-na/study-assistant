package com.example.studyassistant.service;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.service.dto.ConceptRelationExtractionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Tag("Integration")
public class AIConceptRelationExtractorIntegrationTest {

    @Autowired
    private AIConceptRelationExtractor extractor;

    @Autowired
    private ConceptRelationRepository conceptRelationRepository;

    @Autowired
    private ConceptRelationSourceRepository conceptRelationSourceRepository;

    @Autowired
    private ConceptSourceRepository conceptSourceRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private NotebookRepository notebookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private LlmProvider llmProvider;

    private User testUser;
    private Notebook testNotebook;
    private Notebook testNotebookB;
    private Document testDoc;
    private DocumentChunk testChunk;
    private Concept conceptLinearRegression;
    private Concept conceptMSE;
    private Concept conceptExternal;
    private ConceptSource sourceLinearRegression;
    private ConceptSource sourceMSE;

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            
            testUser = new User();
            testUser.setUsername("user_" + suffix);
            testUser.setEmail("user_" + suffix + "@example.com");
            testUser.setPassword("hash");
            userRepository.saveAndFlush(testUser);

            testNotebook = new Notebook();
            testNotebook.setTitle("Notebook A " + suffix);
            testNotebook.setUser(testUser);
            notebookRepository.saveAndFlush(testNotebook);

            testNotebookB = new Notebook();
            testNotebookB.setTitle("Notebook B " + suffix);
            testNotebookB.setUser(testUser);
            notebookRepository.saveAndFlush(testNotebookB);

            testDoc = new Document();
            testDoc.setUser(testUser);
            testDoc.setNotebook(testNotebook);
            testDoc.setFileName("doc_" + suffix + ".txt");
            testDoc.setFilePath("/tmp/" + suffix);
            documentRepository.saveAndFlush(testDoc);

            testChunk = new DocumentChunk();
            testChunk.setDocument(testDoc);
            testChunk.setChunkIndex(0);
            testChunk.setContent("Linear regression minimizes mean squared error during model fitting.");
            testChunk.setTokenCount(10);
            testChunk.setIndexVersion("v1");
            documentChunkRepository.saveAndFlush(testChunk);

            conceptLinearRegression = new Concept();
            conceptLinearRegression.setNotebook(testNotebook);
            conceptLinearRegression.setCanonicalName("Linear Regression " + suffix);
            conceptLinearRegression.setNormalizedName("linear regression " + suffix);
            conceptRepository.saveAndFlush(conceptLinearRegression);

            conceptMSE = new Concept();
            conceptMSE.setNotebook(testNotebook);
            conceptMSE.setCanonicalName("Mean Squared Error " + suffix);
            conceptMSE.setNormalizedName("mean squared error " + suffix);
            conceptRepository.saveAndFlush(conceptMSE);

            conceptExternal = new Concept();
            conceptExternal.setNotebook(testNotebook);
            conceptExternal.setCanonicalName("External Concept " + suffix);
            conceptExternal.setNormalizedName("external concept " + suffix);
            conceptRepository.saveAndFlush(conceptExternal);

            sourceLinearRegression = new ConceptSource();
            sourceLinearRegression.setConcept(conceptLinearRegression);
            sourceLinearRegression.setDocumentChunk(testChunk);
            sourceLinearRegression.setEvidenceText("Linear regression");
            conceptSourceRepository.saveAndFlush(sourceLinearRegression);

            sourceMSE = new ConceptSource();
            sourceMSE.setConcept(conceptMSE);
            sourceMSE.setDocumentChunk(testChunk);
            sourceMSE.setEvidenceText("mean squared error");
            conceptSourceRepository.saveAndFlush(sourceMSE);

            return null;
        });
    }

    @AfterEach
    void tearDown() {
        Set<Long> relationIds = new HashSet<>();
        if (conceptLinearRegression != null && conceptLinearRegression.getId() != null) {
            conceptRelationRepository.findByConceptId(conceptLinearRegression.getId()).forEach(r -> relationIds.add(r.getId()));
        }
        if (conceptMSE != null && conceptMSE.getId() != null) {
            conceptRelationRepository.findByConceptId(conceptMSE.getId()).forEach(r -> relationIds.add(r.getId()));
        }
        if (conceptExternal != null && conceptExternal.getId() != null) {
            conceptRelationRepository.findByConceptId(conceptExternal.getId()).forEach(r -> relationIds.add(r.getId()));
        }

        if (!relationIds.isEmpty()) {
            List<ConceptRelationSource> sourcesToDelete = new ArrayList<>();
            for (Long relId : relationIds) {
                sourcesToDelete.addAll(conceptRelationSourceRepository.findByConceptRelationId(relId));
            }
            if (!sourcesToDelete.isEmpty()) {
                conceptRelationSourceRepository.deleteAllInBatch(sourcesToDelete);
            }
            
            List<ConceptRelation> relationsToDelete = new ArrayList<>();
            for (Long relId : relationIds) {
                conceptRelationRepository.findById(relId).ifPresent(relationsToDelete::add);
            }
            if (!relationsToDelete.isEmpty()) {
                conceptRelationRepository.deleteAllInBatch(relationsToDelete);
            }
        }

        if (sourceMSE != null && sourceMSE.getId() != null) conceptSourceRepository.delete(sourceMSE);
        if (sourceLinearRegression != null && sourceLinearRegression.getId() != null) conceptSourceRepository.delete(sourceLinearRegression);
        
        if (conceptExternal != null && conceptExternal.getId() != null) conceptRepository.delete(conceptExternal);
        if (conceptMSE != null && conceptMSE.getId() != null) conceptRepository.delete(conceptMSE);
        if (conceptLinearRegression != null && conceptLinearRegression.getId() != null) conceptRepository.delete(conceptLinearRegression);
        
        if (testChunk != null && testChunk.getId() != null) documentChunkRepository.delete(testChunk);
        if (testDoc != null && testDoc.getId() != null) documentRepository.delete(testDoc);
        if (testNotebookB != null && testNotebookB.getId() != null) notebookRepository.delete(testNotebookB);
        if (testNotebook != null && testNotebook.getId() != null) notebookRepository.delete(testNotebook);
        if (testUser != null && testUser.getId() != null) userRepository.delete(testUser);
    }

    @Test
    void test1_validGroundedRelationEndToEnd() {
        String mockResponse = "{\n" +
                "  \"relations\": [\n" +
                "    {\n" +
                "      \"sourceConceptId\": " + conceptLinearRegression.getId() + ",\n" +
                "      \"targetConceptId\": " + conceptMSE.getId() + ",\n" +
                "      \"relationType\": \"RELATED_TO\",\n" +
                "      \"confidence\": 0.95,\n" +
                "      \"evidenceText\": \"Linear regression minimizes mean squared error\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(mockResponse);

        ConceptRelationExtractionResult result = extractor.extractRelationsForChunk(testNotebook, testChunk);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getRejectedCount()).isEqualTo(0);
        assertThat(result.getAcceptedRelations()).hasSize(1);

        List<ConceptRelation> rels = conceptRelationRepository.findBySourceConceptId(conceptLinearRegression.getId());
        assertThat(rels).hasSize(1);
        ConceptRelation rel = rels.get(0);
        assertThat(rel.getTargetConcept().getId()).isEqualTo(conceptMSE.getId());
        assertThat(rel.getRelationType()).isEqualTo(RelationType.RELATED_TO);
        assertThat(rel.getConfidence()).isEqualTo(0.95);

        List<ConceptRelationSource> srcs = conceptRelationSourceRepository.findByConceptRelationId(rel.getId());
        assertThat(srcs).hasSize(1);
        ConceptRelationSource src = srcs.get(0);
        assertThat(src.getDocumentChunk().getId()).isEqualTo(testChunk.getId());
        assertThat(src.getEvidenceText()).isEqualTo("Linear regression minimizes mean squared error");
        assertThat(src.getConfidence()).isEqualTo(0.95);

        verify(llmProvider, times(1)).generate(anyString(), anyString(), anyDouble());
    }

    @Test
    void test2_trustedCandidateEnforcement() {
        String mockResponse = "{\n" +
                "  \"relations\": [\n" +
                "    {\n" +
                "      \"sourceConceptId\": " + conceptLinearRegression.getId() + ",\n" +
                "      \"targetConceptId\": " + conceptExternal.getId() + ",\n" +
                "      \"relationType\": \"RELATED_TO\",\n" +
                "      \"confidence\": 0.95,\n" +
                "      \"evidenceText\": \"Linear regression minimizes mean squared error\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(mockResponse);

        ConceptRelationExtractionResult result = extractor.extractRelationsForChunk(testNotebook, testChunk);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getRejectedCount()).isEqualTo(1);
        assertThat(result.getAcceptedRelations()).isEmpty();

        List<ConceptRelation> rels = conceptRelationRepository.findBySourceConceptId(conceptLinearRegression.getId());
        assertThat(rels).isEmpty();

        verify(llmProvider, times(1)).generate(anyString(), anyString(), anyDouble());
    }

    @Test
    void test3_evidenceGrounding() {
        String mockResponse = "{\n" +
                "  \"relations\": [\n" +
                "    {\n" +
                "      \"sourceConceptId\": " + conceptLinearRegression.getId() + ",\n" +
                "      \"targetConceptId\": " + conceptMSE.getId() + ",\n" +
                "      \"relationType\": \"RELATED_TO\",\n" +
                "      \"confidence\": 0.95,\n" +
                "      \"evidenceText\": \"Linear regression minimizes error\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        when(llmProvider.generate(anyString(), anyString(), anyDouble())).thenReturn(mockResponse);

        ConceptRelationExtractionResult result = extractor.extractRelationsForChunk(testNotebook, testChunk);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getRejectedCount()).isEqualTo(1);
        assertThat(result.getAcceptedRelations()).isEmpty();

        List<ConceptRelation> rels = conceptRelationRepository.findBySourceConceptId(conceptLinearRegression.getId());
        assertThat(rels).isEmpty();

        verify(llmProvider, times(1)).generate(anyString(), anyString(), anyDouble());
    }

    @Test
    void test4_ownershipIsolation() {
        ConceptRelationExtractionResult result = extractor.extractRelationsForChunk(testNotebookB, testChunk);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getRejectedCount()).isEqualTo(0);
        assertThat(result.getAcceptedRelations()).isEmpty();

        List<ConceptRelation> rels = conceptRelationRepository.findBySourceConceptId(conceptLinearRegression.getId());
        assertThat(rels).isEmpty();

        verify(llmProvider, never()).generate(anyString(), anyString(), anyDouble());
    }
}
