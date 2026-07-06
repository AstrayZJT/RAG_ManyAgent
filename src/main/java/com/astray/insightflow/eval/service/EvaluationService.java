package com.astray.insightflow.eval.service;

import com.astray.insightflow.agent.planner.PlanResult;
import com.astray.insightflow.agent.verifier.VerifiedClaimStatus;
import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.agent.writer.ReportSection;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.eval.api.EvaluationResponse;
import com.astray.insightflow.eval.domain.EvaluationRecord;
import com.astray.insightflow.eval.persistence.EvaluationRecordRepository;
import com.astray.insightflow.graph.checkpoint.CheckpointService;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.task.service.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EvaluationService {

    private final EvaluationRecordRepository evaluationRecordRepository;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final TaskService taskService;
    private final CheckpointService checkpointService;
    private final JsonUtils jsonUtils;

    public EvaluationService(EvaluationRecordRepository evaluationRecordRepository,
                             EvidenceRecordRepository evidenceRecordRepository,
                             TaskService taskService,
                             CheckpointService checkpointService,
                             JsonUtils jsonUtils) {
        this.evaluationRecordRepository = evaluationRecordRepository;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.taskService = taskService;
        this.checkpointService = checkpointService;
        this.jsonUtils = jsonUtils;
    }

    @Transactional
    public EvaluationResponse evaluate(String taskId) {
        taskService.getTask(taskId);
        ResearchState latestState = checkpointService.loadLatestState(taskId).orElseGet(() -> new ResearchState(Map.of()));

        PlanResult plan = latestState.plan();
        ReportDraft reportDraft = latestState.reportDraft();

        int internalEvidenceCount = (int) evidenceRecordRepository.countByTaskIdAndSourceType(taskId, EvidenceSourceType.INTERNAL);
        int externalEvidenceCount = (int) evidenceRecordRepository.countByTaskIdAndSourceType(taskId, EvidenceSourceType.EXTERNAL);
        int mergedEvidenceCount = latestState.mergedEvidences().isEmpty()
                ? internalEvidenceCount + externalEvidenceCount
                : latestState.mergedEvidences().size();
        int retrievalHitCount = mergedEvidenceCount;

        int subQueryCount = Math.max(1, plan.getSubQueries().size());
        double retrievalHitRate = clamp(mergedEvidenceCount / (double) subQueryCount);
        double citationCoverage = clamp(latestState.claims().stream()
                .filter(claim -> !claim.getSupportingEvidenceIds().isEmpty())
                .count() / (double) Math.max(1, latestState.claims().size()));

        long supportedClaims = latestState.claims().stream()
                .filter(claim -> claim.getStatus() == VerifiedClaimStatus.SUPPORTED)
                .count();
        long partialClaims = latestState.claims().stream()
                .filter(claim -> claim.getStatus() == VerifiedClaimStatus.PARTIAL)
                .count();
        long conflictingClaims = latestState.claims().stream()
                .filter(claim -> claim.getStatus() == VerifiedClaimStatus.CONFLICTING)
                .count();
        long insufficientClaims = latestState.claims().stream()
                .filter(claim -> claim.getStatus() == VerifiedClaimStatus.INSUFFICIENT)
                .count();
        double claimSupportRate = latestState.claims().isEmpty()
                ? 0D
                : clamp((supportedClaims + (partialClaims * 0.5D)) / latestState.claims().size());

        int expectedSections = Math.max(1, plan.getDimensions().size());
        long completedSections = reportDraft.getSections().stream()
                .filter(section -> StringUtils.hasText(section.getContent()))
                .count();
        double sectionCoverage = completedSections / (double) expectedSections;
        double reportCompleteness = clamp((sectionCoverage * 0.70D)
                + (StringUtils.hasText(reportDraft.getExecutiveSummary()) ? 0.15D : 0D)
                + (StringUtils.hasText(reportDraft.getClosingSummary()) ? 0.15D : 0D));

        int lowConfidenceSectionCount = (int) reportDraft.getSections().stream()
                .filter(ReportSection::isLowConfidence)
                .count();
        boolean reviewApproved = latestState.reviewResult().isApproved();

        double overallScore = clamp((retrievalHitRate * 0.20D)
                + (citationCoverage * 0.30D)
                + (claimSupportRate * 0.25D)
                + (reportCompleteness * 0.25D)
                - Math.min(0.15D, lowConfidenceSectionCount * 0.03D));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("subQueryCount", plan.getSubQueries().size());
        details.put("supportedClaimCount", supportedClaims);
        details.put("partialClaimCount", partialClaims);
        details.put("conflictingClaimCount", conflictingClaims);
        details.put("insufficientClaimCount", insufficientClaims);
        details.put("expectedSectionCount", expectedSections);
        details.put("completedSectionCount", completedSections);
        details.put("reportTitle", reportDraft.getTitle());
        details.put("reviewSummary", latestState.reviewResult().getSummary());
        details.put("confidenceNote", reportDraft.getConfidenceNote());

        EvaluationRecord record = new EvaluationRecord();
        record.setId(UUID.randomUUID().toString());
        record.setTaskId(taskId);
        record.setRetrievalHitCount(retrievalHitCount);
        record.setInternalEvidenceCount(internalEvidenceCount);
        record.setExternalEvidenceCount(externalEvidenceCount);
        record.setMergedEvidenceCount(mergedEvidenceCount);
        record.setRetrievalHitRate(retrievalHitRate);
        record.setCitationCoverage(citationCoverage);
        record.setClaimSupportRate(claimSupportRate);
        record.setReportCompleteness(reportCompleteness);
        record.setOverallScore(overallScore);
        record.setLowConfidenceSectionCount(lowConfidenceSectionCount);
        record.setReviewApproved(reviewApproved);
        record.setDetailsJson(jsonUtils.toJson(details));
        record.setCreatedAt(Instant.now());
        evaluationRecordRepository.save(record);

        return toResponse(record);
    }

    public java.util.Optional<EvaluationResponse> latest(String taskId) {
        return evaluationRecordRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId)
                .map(this::toResponse);
    }

    @SuppressWarnings("unchecked")
    private EvaluationResponse toResponse(EvaluationRecord record) {
        Map<String, Object> details = record.getDetailsJson() == null
                ? Map.of()
                : jsonUtils.fromJson(record.getDetailsJson(), Map.class);
        return new EvaluationResponse(
                record.getTaskId(),
                record.getId(),
                record.getRetrievalHitCount(),
                record.getInternalEvidenceCount(),
                record.getExternalEvidenceCount(),
                record.getMergedEvidenceCount(),
                record.getRetrievalHitRate(),
                record.getCitationCoverage(),
                record.getClaimSupportRate(),
                record.getReportCompleteness(),
                record.getOverallScore(),
                record.getLowConfidenceSectionCount(),
                record.isReviewApproved(),
                record.getCreatedAt(),
                details
        );
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }
}
