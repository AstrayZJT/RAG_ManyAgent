package com.astray.insightflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "insightflow.workflow")
public record WorkflowProperties(
        int internalEvidenceThreshold,
        int minClaimCount,
        double verifyPassConfidence,
        double reviewPassCitationCoverage,
        int maxLoops
) {
}
