package com.example.studyassistant.domain.quiz.generation;

import com.example.studyassistant.entity.*;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.repository.*;
import com.example.studyassistant.util.PromptSanitizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroundedQuizGenerationService {

    private final QuizGenerationProperties props;
    private final QuizScopeValidator scopeValidator;
    private final QuizActiveChunkSelector chunkSelector;
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final NotebookRepository notebookRepository;
    private final DocumentRepository documentRepository;
    private final TopicRepository topicRepository;
    private final ConceptRepository conceptRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizQuestionOptionRepository quizQuestionOptionRepository;
    private final QuizQuestionAcceptedAnswerRepository quizQuestionAcceptedAnswerRepository;
    private final QuizQuestionSourceRepository quizQuestionSourceRepository;
    private final QuizQuestionConceptRepository quizQuestionConceptRepository;
    private final TransactionTemplate transactionTemplate;

    public GroundedQuizGenerationResult generate(GroundedQuizGenerationCommand cmd) {
        scopeValidator.validateScope(cmd);

        List<DocumentChunk> evidenceChunks = chunkSelector.selectEvidence(cmd);
        if (evidenceChunks.isEmpty()) {
            throw new IllegalArgumentException("No authorized active evidence chunks found");
        }

        Map<String, DocumentChunk> sourceMap = new LinkedHashMap<>();
        for (int i = 0; i < evidenceChunks.size(); i++) {
            sourceMap.put("S" + (i + 1), evidenceChunks.get(i));
        }

        assertNotInWriteTransaction();
        int count = resolveCount(cmd, evidenceChunks.size());
        int batchSize = Math.max(1, Math.min(5, props.getQuestionsPerBatch()));
        int batches = (count + batchSize - 1) / batchSize;
        List<String> aliases = new ArrayList<>(sourceMap.keySet());
        List<ValidatedQuizQuestion> validated = new ArrayList<>();
        Set<String> seenQuestions = new HashSet<>();
        int failedBatches = 0;
        for (int b = 0; b < batches; b++) {
            int from = (int) ((long) b * aliases.size() / batches);
            int to = (int) ((long) (b + 1) * aliases.size() / batches);
            // Reuse evidence for short documents without asking for 50 questions in one call.
            if (to == from) to = from + 1;
            List<String> batchAliases = aliases.subList(from, to);
            Map<String, DocumentChunk> batchMap = new LinkedHashMap<>();
            int inputCount = Math.min(12, batchAliases.size());
            for (int i = 0; i < inputCount; i++) {
                int offset = inputCount == 1 ? 0 : (int) ((long) i * (batchAliases.size() - 1) / (inputCount - 1));
                String alias = batchAliases.get(offset);
                batchMap.put(alias, sourceMap.get(alias));
            }
            int batchCount = Math.min(batchSize, count - b * batchSize);
            try {
                String raw = llmProvider.generate(buildSystemPrompt(batchMap, batchCount, cmd.allowedQuestionTypes()),
                        "Generate " + batchCount + " questions grounded in the provided source data. Return valid JSON only.",
                        props.getTemperature());
                int accepted = 0;
                for (ValidatedQuizQuestion question : validateQuestions(parseResponse(raw), batchMap, cmd)) {
                    if (seenQuestions.add(QuizContentHasher.normalizeShortAnswer(question.questionText))) {
                        validated.add(question);
                        if (++accepted == batchCount) break;
                    }
                }
            } catch (Exception e) {
                failedBatches++;
                log.warn("Quiz generation batch {}/{} failed", b + 1, batches, e);
            }
        }
        if (validated.isEmpty()) {
            if (failedBatches > 0) throw new RuntimeException("LLM failure during quiz generation");
            throw new IllegalArgumentException("No valid questions generated");
        }
        return transactionTemplate.execute(status -> persistBatchInsideTransaction(cmd, validated, sourceMap, count));
    }

    private int resolveCount(GroundedQuizGenerationCommand cmd, int chunkCount) {
        int maximum = Math.max(1, Math.min(50, props.getMaxCount()));
        if (cmd.questionCount() != null && cmd.questionCount() > 0) return Math.min(cmd.questionCount(), maximum);
        int auto = (int) Math.round(chunkCount * props.getQuestionsPerChunk());
        return Math.max(1, Math.min(maximum, Math.max(props.getMinAutoCount(), Math.min(auto, props.getMaxAutoCount()))));
    }

    private void assertNotInWriteTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Quiz LLM generation must run outside a database transaction");
        }
    }

    private String buildSystemPrompt(Map<String, DocumentChunk> sourceMap, int count, List<QuizQuestionType> types) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a strict quiz generation assistant.\n");
        sb.append("Generate exactly ").append(count).append(" high-quality questions.\n");
        sb.append("STRICT RULES:\n");
        sb.append("- Use ONLY the SOURCE DATA below.\n");
        sb.append("- Write questions, options and explanations in the same language as the SOURCE DATA.\n");
        sb.append("- Allowed question types: ").append(types == null ? Arrays.toString(QuizQuestionType.values()) : types).append(".\n");
        sb.append("- Create distinct questions about different facts; keep explanations concise.\n");
        sb.append("- Cite sources using [S1], [S2] etc.\n");
        sb.append("- Return valid JSON exactly matching this structure:\n");
        sb.append("{ \"questions\": [ { \"type\": \"MCQ_SINGLE|MCQ_MULTI|TRUE_FALSE|SHORT_ANSWER\", \"questionText\": \"...\", \"points\": 1.0, \"explanation\": \"...\", \"sources\": [\"S1\"], \"options\": [ { \"text\": \"...\", \"isCorrect\": true } ], \"acceptedAnswers\": [\"answer1\"] } ] }\n");

        sb.append("=== SOURCE DATA ===\n");
        for (Map.Entry<String, DocumentChunk> entry : sourceMap.entrySet()) {
            sb.append("[").append(entry.getKey()).append("]\n");
            sb.append(PromptSanitizer.sanitizeUntrustedPromptText(entry.getValue().getContent())).append("\n\n");
        }
        sb.append("=== END SOURCE DATA ===\n");
        return sb.toString();
    }

    private List<RawQuizQuestion> parseResponse(String raw) {
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf("{");
                int end = cleaned.lastIndexOf("}");
                if (start >= 0 && end >= 0) cleaned = cleaned.substring(start, end + 1);
            }
            Map<String, Object> root = objectMapper.readValue(cleaned, new TypeReference<>() {});
            List<?> list = (List<?>) root.get("questions");
            if (list == null) throw new IllegalArgumentException("Missing 'questions' key");

            List<RawQuizQuestion> results = new ArrayList<>();
            for (Object value : list) {
                try {
                    if (!(value instanceof Map<?, ?> q)) continue;
                    RawQuizQuestion rawQ = new RawQuizQuestion();
                    rawQ.type = (String) q.get("type");
                    rawQ.questionText = (String) q.get("questionText");
                    rawQ.points = q.get("points") instanceof Number ? ((Number) q.get("points")).doubleValue() : 1.0;
                    rawQ.explanation = (String) q.get("explanation");
                    rawQ.sources = q.get("sources") instanceof List ? (List<String>) q.get("sources") : new ArrayList<>();

                    List<Map<String, Object>> opts = (List<Map<String, Object>>) q.get("options");
                    if (opts != null) {
                        for (Map<String, Object> o : opts) {
                            rawQ.options.add(new RawOption((String) o.get("text"), Boolean.TRUE.equals(o.get("isCorrect"))));
                        }
                    }

                    List<String> acc = (List<String>) q.get("acceptedAnswers");
                    if (acc != null) {
                        for (String a : acc) {
                            if (a != null && !a.isBlank()) rawQ.acceptedAnswers.add(a);
                        }
                    }
                    results.add(rawQ);
                } catch (RuntimeException invalidQuestion) {
                    log.debug("Skipping malformed quiz question");
                }
            }
            return results;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed LLM response", e);
        }
    }

    private List<ValidatedQuizQuestion> validateQuestions(List<RawQuizQuestion> raw, Map<String, DocumentChunk> sourceMap, GroundedQuizGenerationCommand cmd) {
        List<ValidatedQuizQuestion> validated = new ArrayList<>();
        for (RawQuizQuestion r : raw) {
            try {
                if (r.questionText == null || r.questionText.isBlank()) continue;

                QuizQuestionType type;
                try {
                    type = QuizQuestionType.valueOf(r.type);
                } catch (Exception e) {
                    continue; // invalid type
                }
                if (cmd.allowedQuestionTypes() != null && !cmd.allowedQuestionTypes().contains(type)) continue;

                List<String> validSources = new ArrayList<>();
                for (String rawAlias : r.sources) {
                    String s = rawAlias == null ? "" : rawAlias.trim();
                    if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1).trim();
                    if (sourceMap.containsKey(s)) {
                        if (!validSources.contains(s)) validSources.add(s);
                    } else {
                        throw new IllegalArgumentException("LLM cited invalid source alias: " + s);
                    }
                }
                if (validSources.isEmpty()) throw new IllegalArgumentException("Question lacks valid source citations");
                if (validSources.size() > props.getMaxSourcesPerQuestion()) throw new IllegalArgumentException("Too many source citations");
                if (r.options.stream().anyMatch(o -> o.text == null || o.text.isBlank())) {
                    throw new IllegalArgumentException("Question has empty option text");
                }

                if (type == QuizQuestionType.MCQ_SINGLE) {
                    if (r.options.size() < 2) throw new IllegalArgumentException("MCQ_SINGLE requires >= 2 options");
                    long correctCount = r.options.stream().filter(o -> o.isCorrect).count();
                    if (correctCount != 1) throw new IllegalArgumentException("MCQ_SINGLE requires exactly 1 correct option");
                } else if (type == QuizQuestionType.MCQ_MULTI) {
                    if (r.options.size() < 2) throw new IllegalArgumentException("MCQ_MULTI requires >= 2 options");
                    long correctCount = r.options.stream().filter(o -> o.isCorrect).count();
                    if (correctCount < 1) throw new IllegalArgumentException("MCQ_MULTI requires >= 1 correct option");
                } else if (type == QuizQuestionType.TRUE_FALSE) {
                    if (r.options.size() != 2) throw new IllegalArgumentException("TRUE_FALSE requires exactly 2 options");
                    long correctCount = r.options.stream().filter(o -> o.isCorrect).count();
                    if (correctCount != 1) throw new IllegalArgumentException("TRUE_FALSE requires exactly 1 correct option");
                    boolean hasTrue = r.options.stream().anyMatch(o -> "true".equalsIgnoreCase(o.text));
                    boolean hasFalse = r.options.stream().anyMatch(o -> "false".equalsIgnoreCase(o.text));
                    if (!hasTrue || !hasFalse) throw new IllegalArgumentException("TRUE_FALSE options must be 'true' and 'false'");
                } else if (type == QuizQuestionType.SHORT_ANSWER) {
                    if (r.acceptedAnswers.isEmpty()) throw new IllegalArgumentException("SHORT_ANSWER requires >= 1 accepted answer");
                }

                ValidatedQuizQuestion vq = new ValidatedQuizQuestion();
                vq.type = type;
                vq.questionText = r.questionText.trim();
                vq.points = r.points > 0 ? r.points : 1.0;
                vq.explanation = r.explanation;
                vq.sources = validSources;
                vq.options = r.options;

                for (String a : r.acceptedAnswers) {
                    vq.acceptedAnswers.add(QuizContentHasher.normalizeShortAnswer(a));
                }

                // Build hash
                StringBuilder hashInput = new StringBuilder();
                hashInput.append(vq.type).append("|").append(vq.questionText).append("|").append(vq.points);
                for (RawOption o : vq.options) hashInput.append("|").append(o.text).append(":").append(o.isCorrect);
                for (String a : vq.acceptedAnswers) hashInput.append("|").append(a);

                vq.contentHash = QuizContentHasher.hash(hashInput.toString());
                validated.add(vq);
            } catch (RuntimeException invalidQuestion) {
                log.debug("Skipping invalid quiz question: {}", invalidQuestion.getMessage());
            }
        }
        return validated;
    }

    private GroundedQuizGenerationResult persistBatchInsideTransaction(GroundedQuizGenerationCommand cmd, List<ValidatedQuizQuestion> validated, Map<String, DocumentChunk> sourceMap, int requestedCount) {
        if (validated.isEmpty()) {
            throw new IllegalArgumentException("No valid questions generated");
        }

        User user = userRepository.findById(cmd.userId()).orElseThrow();
        // DOCUMENT scope derives its optional notebook from the owned document.
        validateActiveSources(cmd, validated, sourceMap);
        scopeValidator.validateScope(cmd);
        Document targetDocument = cmd.scopeType() == QuizScopeType.DOCUMENT
                ? documentRepository.findKnowledgeGraphDocumentByIdAndUserId(cmd.documentId(), cmd.userId()).orElseThrow()
                : null;
        Notebook notebook = targetDocument != null ? targetDocument.getNotebook()
                : notebookRepository.findById(cmd.notebookId()).orElseThrow();

        Quiz quiz = new Quiz();
        quiz.setTitle(cmd.title() != null ? cmd.title() : "Generated Quiz");
        quiz.setNotebook(notebook);
        quiz.setScopeType(cmd.scopeType());
        quiz.setMode(cmd.mode() != null ? cmd.mode() : QuizMode.PRACTICE);
        quiz.setStatus(QuizStatus.DRAFT);
        if (targetDocument != null) quiz.setDocument(targetDocument);
        else if (cmd.documentId() != null) quiz.setDocument(documentRepository.findById(cmd.documentId()).orElse(null));
        if (cmd.topicId() != null) quiz.setTopic(topicRepository.findById(cmd.topicId()).orElse(null));
        if (cmd.scopeConceptId() != null) quiz.setScopeConcept(conceptRepository.findById(cmd.scopeConceptId()).orElse(null));

        quiz.setTimeLimitSeconds(cmd.timeLimitSeconds());
        quiz.setPassThreshold(cmd.passThreshold());
        quiz = quizRepository.save(quiz);

        List<GeneratedQuizQuestionResult> results = new ArrayList<>();
        int order = 1;

        for (ValidatedQuizQuestion vq : validated) {
            QuizQuestion q = new QuizQuestion();
            q.setQuiz(quiz);
            q.setQuestionType(vq.type);
            q.setQuestion(vq.questionText);
            q.setPoints(vq.points);
            q.setExplanation(vq.explanation);
            q.setContentHash(vq.contentHash);
            q.setQuestionOrder(order++);

            // Legacy remain null
            q = quizQuestionRepository.save(q);

            int optOrder = 1;
            for (RawOption o : vq.options) {
                QuizQuestionOption qo = new QuizQuestionOption();
                qo.setQuestion(q);
                qo.setOptionOrder(optOrder++);
                qo.setOptionText(o.text);
                qo.setIsCorrect(o.isCorrect);
                quizQuestionOptionRepository.save(qo);
            }

            int accOrder = 1;
            for (String a : vq.acceptedAnswers) {
                QuizQuestionAcceptedAnswer qa = new QuizQuestionAcceptedAnswer();
                qa.setQuestion(q);
                qa.setAcceptedOrder(accOrder++);
                qa.setAcceptedText(a);
                quizQuestionAcceptedAnswerRepository.save(qa);
            }

            int srcOrder = 1;
            List<Long> chunkIds = new ArrayList<>();
            for (String alias : vq.sources) {
                DocumentChunk chunk = sourceMap.get(alias);
                QuizQuestionSource qs = new QuizQuestionSource();
                qs.setQuestion(q);
                qs.setDocumentChunk(chunk);
                qs.setSourceOrder(srcOrder++);
                quizQuestionSourceRepository.save(qs);
                chunkIds.add(chunk.getId());
            }

            // Concept linking
            if (cmd.scopeConceptId() != null) {
                Concept concept = conceptRepository.findById(cmd.scopeConceptId()).orElse(null);
                if (concept != null) {
                    QuizQuestionConcept qc = new QuizQuestionConcept();
                    qc.setQuestion(q);
                    qc.setConcept(concept);
                    quizQuestionConceptRepository.save(qc);
                }
            }

            results.add(new GeneratedQuizQuestionResult(q.getId(), q.getQuestionType(), q.getQuestion(), q.getPoints(), q.getContentHash(), chunkIds));
        }

        // Publish only after the validated questions, options and provenance have
        // been saved in this same transaction. Attempt start still rejects drafts.
        quiz.setStatus(QuizStatus.PUBLISHED);
        quizRepository.save(quiz);
        return new GroundedQuizGenerationResult(quiz.getId(), requestedCount, results.size(), results);
    }

    private void validateActiveSources(GroundedQuizGenerationCommand cmd, List<ValidatedQuizQuestion> questions,
                                       Map<String, DocumentChunk> sourceMap) {
        Map<Long, Document> locked = new TreeMap<>();
        for (ValidatedQuizQuestion question : questions) {
            for (String alias : question.sources) {
                locked.put(sourceMap.get(alias).getDocument().getId(), null);
            }
        }
        for (Long documentId : new ArrayList<>(locked.keySet())) {
            Document live = documentRepository.findByIdWithPessimisticLock(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Quiz evidence document is no longer available"));
            if (live.getUser() == null || !Objects.equals(live.getUser().getId(), cmd.userId())
                    || !"READY".equals(live.getStatus())) {
                throw new IllegalArgumentException("Quiz evidence is no longer authorized or READY");
            }
            if (cmd.scopeType() != QuizScopeType.DOCUMENT
                    && (live.getNotebook() == null || !Objects.equals(live.getNotebook().getId(), cmd.notebookId()))) {
                throw new IllegalArgumentException("Quiz evidence notebook changed during generation; retry");
            }
            locked.put(documentId, live);
        }
        for (ValidatedQuizQuestion question : questions) {
            for (String alias : question.sources) {
                DocumentChunk chunk = sourceMap.get(alias);
                Document live = locked.get(chunk.getDocument().getId());
                if (chunk.getIndexVersion() == null || !chunk.getIndexVersion().equals(live.getActiveIndexVersion())) {
                    throw new IllegalArgumentException("Quiz evidence index changed during generation; retry");
                }
            }
        }
    }

    private static class RawQuizQuestion {
        String type;
        String questionText;
        Double points;
        String explanation;
        List<String> sources = new ArrayList<>();
        List<RawOption> options = new ArrayList<>();
        List<String> acceptedAnswers = new ArrayList<>();
    }
    private static class RawOption {
        String text;
        boolean isCorrect;
        RawOption(String t, boolean c) { text = t; isCorrect = c; }
    }
    private static class ValidatedQuizQuestion {
        QuizQuestionType type;
        String questionText;
        Double points;
        String explanation;
        String contentHash;
        List<String> sources = new ArrayList<>();
        List<RawOption> options = new ArrayList<>();
        List<String> acceptedAnswers = new ArrayList<>();
    }
}
