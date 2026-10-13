package com.example.studyassistant.service.tutor;

import com.example.studyassistant.service.adaptive.AdaptiveCandidateService;
import com.example.studyassistant.service.adaptive.AdaptiveRecommendationService;
import com.example.studyassistant.dto.adaptive.AdaptiveCandidateFactDto;
import com.example.studyassistant.dto.adaptive.AdaptiveRecommendationDto;
import com.example.studyassistant.retrieval.service.GroundedContextService;
import com.example.studyassistant.retrieval.model.GroundedContext;
import com.example.studyassistant.retrieval.service.GroundedCitationService;
import com.example.studyassistant.retrieval.model.CitationDto;
import com.example.studyassistant.retrieval.model.GroundedAnswer;
import com.example.studyassistant.llm.LlmProvider;
import com.example.studyassistant.config.AiChatProperties;
import com.example.studyassistant.dto.TutorSessionState;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class TutorTurnService {

    private final TutorConversationReadService readService;
    private final TutorTurnPersistenceService persistenceService;
    private final AdaptiveCandidateService candidateService;
    private final AdaptiveRecommendationService recommendationService;
    private final TutorPolicyService policyService;
    private final TutorPromptBuilder promptBuilder;
    private final GroundedContextService groundedContextService;
    private final GroundedCitationService citationService;
    private final LlmProvider llmProvider;
    private final AiChatProperties aiChatProperties;

    public TutorTurnService(
            TutorConversationReadService readService,
            TutorTurnPersistenceService persistenceService,
            AdaptiveCandidateService candidateService,
            AdaptiveRecommendationService recommendationService,
            TutorPolicyService policyService,
            TutorPromptBuilder promptBuilder,
            GroundedContextService groundedContextService,
            GroundedCitationService citationService,
            LlmProvider llmProvider,
            AiChatProperties aiChatProperties) {
        this.readService = readService;
        this.persistenceService = persistenceService;
        this.candidateService = candidateService;
        this.recommendationService = recommendationService;
        this.policyService = policyService;
        this.promptBuilder = promptBuilder;
        this.groundedContextService = groundedContextService;
        this.citationService = citationService;
        this.llmProvider = llmProvider;
        this.aiChatProperties = aiChatProperties;
    }

    public TutorTurnResult turn(TutorTurnCommand command) {
        // READ/VALIDATE
        TutorConversationState convState = readService.findConversationState(command.userId(), command.conversationId());
        Long notebookId = convState.notebookId();
        
        List<AdaptiveCandidateFactDto> candidates = candidateService.getCandidateFacts(command.userId(), notebookId);
        Map<Long, AdaptiveCandidateFactDto> candidateMap = candidates.stream()
            .collect(Collectors.toMap(AdaptiveCandidateFactDto::getConceptId, c -> c, (a, b) -> {
                throw new IllegalStateException("Duplicate concept ID in candidate facts");
            }));
            
        // RESOLVE TARGET
        Long targetConceptId = null;
        if (command.conceptId() != null) {
            if (!candidateMap.containsKey(command.conceptId())) {
                throw new IllegalArgumentException("Explicit concept not in active candidate map");
            }
            targetConceptId = command.conceptId();
        } else {
            TutorSessionState sessionState = readService.findSessionState(command.userId(), command.conversationId()).orElse(null);
            if (sessionState != null && sessionState.currentConceptId() != null && candidateMap.containsKey(sessionState.currentConceptId())) {
                targetConceptId = sessionState.currentConceptId();
            } else {
                List<AdaptiveRecommendationDto> recs = recommendationService.getRecommendations(command.userId(), notebookId, 1);
                if (!recs.isEmpty()) {
                    targetConceptId = recs.get(0).getConceptId();
                    if (!candidateMap.containsKey(targetConceptId)) {
                        throw new IllegalStateException("Recommended concept missing from candidate map");
                    }
                }
            }
        }
        
        AdaptiveCandidateFactDto targetCandidate = targetConceptId != null ? candidateMap.get(targetConceptId) : null;
        
        // BUILD PREREQUISITE STATE
        List<TutorPrerequisiteState> prereqStates = new ArrayList<>();
        if (targetCandidate != null && targetCandidate.getPrerequisiteConceptIds() != null) {
            for (Long pid : targetCandidate.getPrerequisiteConceptIds()) {
                AdaptiveCandidateFactDto pFact = candidateMap.get(pid);
                if (pFact == null) {
                    throw new IllegalStateException("Missing prerequisite in candidate map");
                }
                prereqStates.add(new TutorPrerequisiteState(
                    pFact.getConceptId(), pFact.getConceptName(), pFact.getMasteryState(), pFact.getMasteryEstimate(), pFact.getEvidenceCount()
                ));
            }
        }
        
        // POLICY DECISION
        TutorPolicyContext policyContext = new TutorPolicyContext(
            targetConceptId,
            targetCandidate != null ? targetCandidate.getConceptName() : null,
            targetCandidate != null ? targetCandidate.getMasteryState() : null,
            targetCandidate != null ? targetCandidate.getMasteryEstimate() : null,
            targetCandidate != null ? targetCandidate.getEvidenceCount() : 0,
            prereqStates,
            command.requestedMode()
        );
        
        TutorPolicyDecision decision = policyService.decide(policyContext);
        
        // RETRIEVAL QUERY
        String retrievalQuery = command.message();
        if (decision.focusConceptName() != null) {
            retrievalQuery += "\n\nStudy concept: " + decision.focusConceptName();
        } else if (decision.targetConceptName() != null) {
            retrievalQuery += "\n\nStudy concept: " + decision.targetConceptName();
        }
        
        // READ HISTORY
        List<TutorHistoryMessage> history = readService.findRecentHistory(command.conversationId());
        
        // GROUNDED CONTEXT
        long remainingBudget = aiChatProperties.getContextWindowTokens() - aiChatProperties.getMaxOutputTokens() - aiChatProperties.getInputSafetyMarginTokens();
        GroundedContext groundedContext = groundedContextService.buildContext(command.userId(), notebookId, retrievalQuery, remainingBudget);
        
        if (!groundedContext.hasSufficientEvidence()) {
            String refusal = "The provided materials do not contain enough information to answer this question.";
            persistenceService.persistTurn(
                command.userId(), command.conversationId(), targetConceptId, command.message(), refusal, new ArrayList<>()
            );
            return new TutorTurnResult(
                command.conversationId(), decision.targetConceptId(), decision.focusConceptId(), decision.mode(), refusal, false, new ArrayList<>()
            );
        }
        
        // PROMPT
        TutorPrompt prompt = promptBuilder.buildPrompt(decision, groundedContext, history, command.message());
        
        // LLM
        String rawLlmOutput = llmProvider.generate(prompt.systemPrompt(), prompt.userPrompt(), 0.2);
        
        if (rawLlmOutput == null || rawLlmOutput.isBlank()) {
            throw new IllegalStateException("Blank LLM output");
        }
        
        // VALIDATE
        GroundedAnswer citationResult = citationService.processCitations(rawLlmOutput, groundedContext.sourceMap());
        List<CitationDto> validCitations = citationResult.getCitations();
        String cleanedLlmOutput = citationResult.getAnswer();
        
        if (decision.mode() == TutorMode.EXPLAIN && (validCitations == null || validCitations.isEmpty())) {
            throw new IllegalStateException("EXPLAIN mode requires at least one valid citation");
        }
        
        // PERSIST
        persistenceService.persistTurn(
            command.userId(), command.conversationId(), targetConceptId, command.message(), cleanedLlmOutput, validCitations
        );
        
        return new TutorTurnResult(
            command.conversationId(), decision.targetConceptId(), decision.focusConceptId(), decision.mode(), cleanedLlmOutput, true, validCitations
        );
    }
}
