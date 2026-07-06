package com.astray.insightflow.eval.api;

import java.time.Instant;
import java.util.Map;

public record EvaluationResponse(
        String taskId,
        String evaluationId,
        int retrievalHitCount,
        int internalEvidenceCount,
        int externalEvidenceCount,
        int mergedEvidenceCount,
        double retrievalHitRate,
        double citationCoverage,
        double claimSupportRate,
        double reportCompleteness,
        double overallScore,
        int lowConfidenceSectionCount,
        boolean reviewApproved,
        Instant createdAt,
        Map<String, Object> details
) {
}
