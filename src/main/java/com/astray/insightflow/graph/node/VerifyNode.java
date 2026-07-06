package com.astray.insightflow.graph.node;

import com.astray.insightflow.agent.verifier.VerificationResult;
import com.astray.insightflow.agent.verifier.VerifiedClaim;
import com.astray.insightflow.agent.verifier.VerifiedClaimEntity;
import com.astray.insightflow.agent.verifier.VerifiedClaimRepository;
import com.astray.insightflow.agent.verifier.VerifiedClaimStatus;
import com.astray.insightflow.agent.verifier.VerifierAgent;
import com.astray.insightflow.agent.verifier.VerifyDecision;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.common.util.MetricsUtils;
import com.astray.insightflow.config.WorkflowProperties;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.tool.CitationTool;
import com.astray.insightflow.tool.TrustScoreTool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class VerifyNode {

    private final VerifierAgent verifierAgent;
    private final CitationTool citationTool;
    private final TrustScoreTool trustScoreTool;
    private final VerifiedClaimRepository verifiedClaimRepository;
    private final JsonUtils jsonUtils;
    private final WorkflowProperties workflowProperties;
    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;

    public VerifyNode(VerifierAgent verifierAgent,
                      CitationTool citationTool,
                      TrustScoreTool trustScoreTool,
                      VerifiedClaimRepository verifiedClaimRepository,
                      JsonUtils jsonUtils,
                      WorkflowProperties workflowProperties,
                      TaskProgressPublisher taskProgressPublisher,
                      AgentRunLogService agentRunLogService) {
        this.verifierAgent = verifierAgent;
        this.citationTool = citationTool;
        this.trustScoreTool = trustScoreTool;
        this.verifiedClaimRepository = verifiedClaimRepository;
        this.jsonUtils = jsonUtils;
        this.workflowProperties = workflowProperties;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
    }

    @Transactional
    public Map<String, Object> execute(ResearchState state) {
        Instant startedAt = Instant.now();
        String taskId = state.taskId();
        taskProgressPublisher.publish(taskId, "verify", "RUNNING", "Claim verification started", Map.of(
                "factCount", state.facts().size()
        ));
        try {
            String factsJson = jsonUtils.toJson(Map.of("items", state.facts()));
            String evidenceJson = jsonUtils.toJson(Map.of("items", state.mergedEvidences()));
            VerificationResult verification = verifierAgent.verify(
                    state.userQuery(),
                    state.language(),
                    factsJson,
                    evidenceJson,
                    state.loopCount()
            );

            List<VerifiedClaim> claims = citationTool.attach(taskId, "verify", verification.getClaims(), state.mergedEvidences());
            Map<String, Double> trustScores = trustScoreTool.scoreBatch(taskId, "verify", state.mergedEvidences());
            double averageConfidence = applyTrustScores(claims, state.mergedEvidences(), trustScores);
            double citationCoverage = MetricsUtils.citationCoverage(claims);
            int conflictCount = (int) claims.stream()
                    .filter(claim -> claim.getStatus() == VerifiedClaimStatus.CONFLICTING)
                    .count();

            VerifyDecision decision = verification.getDecision();
            boolean readyForWrite = claims.size() >= workflowProperties.minClaimCount()
                    && averageConfidence >= workflowProperties.verifyPassConfidence()
                    && citationCoverage >= 0.50D
                    && conflictCount == 0;
            if (!readyForWrite && state.loopCount() > workflowProperties.maxLoops()) {
                decision.setReadyForWrite(true);
                decision.setRerunRetrieval(false);
                decision.setLowConfidenceMode(true);
                decision.setReason("已达到最大补证次数，进入低置信度写作。");
            } else {
                decision.setReadyForWrite(readyForWrite);
                decision.setRerunRetrieval(!readyForWrite);
                decision.setLowConfidenceMode(!readyForWrite);
                decision.setReason(readyForWrite ? "验证通过，可进入写作。" : "验证未达阈值，建议回到检索阶段补证。");
            }
            decision.setSupportRatio(citationCoverage);
            decision.setConflictCount(conflictCount);

            int nextLoopCount = state.loopCount();
            if (!decision.isReadyForWrite() && decision.isRerunRetrieval()) {
                nextLoopCount = state.loopCount() + 1;
            }

            verifiedClaimRepository.deleteByTaskId(taskId);
            verifiedClaimRepository.saveAll(claims.stream().map(claim -> toEntity(taskId, claim)).toList());

            Map<String, Object> metrics = Map.of(
                    "tokenUsage", MetricsUtils.estimateTokens(factsJson, evidenceJson),
                    "citationCoverage", citationCoverage,
                    "avgClaimConfidence", averageConfidence
            );

            Map<String, Object> output = new LinkedHashMap<>();
            output.put(ResearchState.CLAIMS, claims);
            output.put(ResearchState.VERIFY_DECISION, decision);
            output.put(ResearchState.LOOP_COUNT, nextLoopCount);
            output.put(ResearchState.STATUS, decision.isReadyForWrite() ? "VERIFIED" : "VERIFY_NEEDS_RETRIEVAL");
            output.put(ResearchState.METRICS, metrics);
            output.put(ResearchState.TIMELINE, List.of("verify completed"));
            agentRunLogService.logSuccess(taskId, "verify", startedAt, verification, "Claims verified", metrics);
            taskProgressPublisher.publish(taskId, "verify", "COMPLETED", "Claim verification completed", Map.of(
                    "claimCount", claims.size(),
                    "readyForWrite", decision.isReadyForWrite(),
                    "loopCount", nextLoopCount
            ));
            return output;
        } catch (Exception exception) {
            agentRunLogService.logFailure(taskId, "verify", startedAt, exception);
            taskProgressPublisher.publish(taskId, "verify", "FAILED", exception.getMessage(), Map.of());
            throw exception;
        }
    }

    private double applyTrustScores(List<VerifiedClaim> claims, List<Evidence> evidences, Map<String, Double> trustScores) {
        double total = 0D;
        for (VerifiedClaim claim : claims) {
            double trust = claim.getSupportingEvidenceIds().stream()
                    .map(trustScores::get)
                    .filter(value -> value != null)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(evidences.isEmpty() ? 0.50D : 0.70D);
            double adjusted = Math.min(0.98D, (claim.getConfidenceScore() * 0.72D) + (trust * 0.28D));
            claim.setConfidenceScore(adjusted);
            claim.setLowConfidence(adjusted < 0.70D);
            if (claim.getStatus() == VerifiedClaimStatus.PARTIAL && adjusted >= workflowProperties.verifyPassConfidence()) {
                claim.setStatus(VerifiedClaimStatus.SUPPORTED);
            }
            total += adjusted;
        }
        return claims.isEmpty() ? 0D : total / claims.size();
    }

    private VerifiedClaimEntity toEntity(String taskId, VerifiedClaim claim) {
        VerifiedClaimEntity entity = new VerifiedClaimEntity();
        entity.setId(claim.getId());
        entity.setTaskId(taskId);
        entity.setDimensionName(claim.getDimension());
        entity.setClaimText(claim.getClaimText());
        entity.setSupportEvidenceJson(jsonUtils.toJson(claim.getSupportingEvidenceIds()));
        entity.setConflictEvidenceJson(jsonUtils.toJson(claim.getConflictingEvidenceIds()));
        entity.setConfidenceScore(claim.getConfidenceScore());
        entity.setClaimStatus(claim.getStatus().name());
        entity.setRationale(claim.getRationale());
        entity.setLowConfidence(claim.isLowConfidence());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
