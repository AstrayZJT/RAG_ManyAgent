package com.astray.insightflow.eval.citation;

public record CitationMetrics(
        double claimCoverage,
        double reportSectionCoverage,
        double validityRate,
        int totalCitationCount,
        int validCitationCount,
        int invalidCitationCount
) {
}
