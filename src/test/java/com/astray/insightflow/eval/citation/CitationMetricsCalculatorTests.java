package com.astray.insightflow.eval.citation;

import com.astray.insightflow.agent.verifier.VerifiedClaim;
import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.agent.writer.ReportSection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitationMetricsCalculatorTests {

    @Test
    void calculatesCoverageAndValidityAgainstTaskWhitelist() {
        VerifiedClaim covered = new VerifiedClaim();
        covered.setSupportingEvidenceIds(List.of("e-1", "cross-task"));
        VerifiedClaim uncovered = new VerifiedClaim();
        uncovered.setSupportingEvidenceIds(List.of("missing"));

        ReportDraft report = new ReportDraft();
        report.setSections(List.of(
                new ReportSection("covered", "content", List.of("e-1")),
                new ReportSection("uncovered", "content", List.of("missing"))
        ));

        CitationMetrics metrics = new CitationMetricsCalculator().calculate(
                List.of(covered, uncovered),
                report,
                Set.of("e-1")
        );

        assertEquals(0.5D, metrics.claimCoverage());
        assertEquals(0.5D, metrics.reportSectionCoverage());
        assertEquals(0.4D, metrics.validityRate());
        assertEquals(5, metrics.totalCitationCount());
        assertEquals(2, metrics.validCitationCount());
        assertEquals(3, metrics.invalidCitationCount());
    }
}
