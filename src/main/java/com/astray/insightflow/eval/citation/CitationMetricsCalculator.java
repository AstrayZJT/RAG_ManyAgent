package com.astray.insightflow.eval.citation;

import com.astray.insightflow.agent.verifier.VerifiedClaim;
import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.agent.writer.ReportSection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class CitationMetricsCalculator {

    public CitationMetrics calculate(List<VerifiedClaim> claims,
                                     ReportDraft reportDraft,
                                     Set<String> allowedEvidenceIds) {
        List<VerifiedClaim> safeClaims = claims == null ? List.of() : claims;
        List<ReportSection> sections = reportDraft == null || reportDraft.getSections() == null
                ? List.of()
                : reportDraft.getSections();
        Set<String> allowedIds = allowedEvidenceIds == null ? Set.of() : allowedEvidenceIds;

        long coveredClaims = safeClaims.stream()
                .filter(claim -> safe(claim.getSupportingEvidenceIds()).stream().anyMatch(allowedIds::contains))
                .count();
        long coveredSections = sections.stream()
                .filter(section -> safe(section.getEvidenceIds()).stream().anyMatch(allowedIds::contains))
                .count();

        List<String> allCitationIds = new ArrayList<>();
        for (VerifiedClaim claim : safeClaims) {
            allCitationIds.addAll(safe(claim.getSupportingEvidenceIds()));
            allCitationIds.addAll(safe(claim.getConflictingEvidenceIds()));
        }
        for (ReportSection section : sections) {
            allCitationIds.addAll(safe(section.getEvidenceIds()));
        }

        int totalCitationCount = allCitationIds.size();
        int validCitationCount = (int) allCitationIds.stream().filter(allowedIds::contains).count();
        return new CitationMetrics(
                ratio(coveredClaims, safeClaims.size()),
                ratio(coveredSections, sections.size()),
                ratio(validCitationCount, totalCitationCount),
                totalCitationCount,
                validCitationCount,
                totalCitationCount - validCitationCount
        );
    }

    private List<String> safe(List<String> ids) {
        return ids == null ? List.of() : ids;
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0D : numerator / (double) denominator;
    }
}
