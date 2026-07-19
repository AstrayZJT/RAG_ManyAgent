package com.astray.insightflow.tool;

import com.astray.insightflow.agent.verifier.VerifiedClaim;
import com.astray.insightflow.agent.verifier.VerifiedClaimStatus;
import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.agent.writer.ReportSection;
import com.astray.insightflow.observe.service.ToolCallLogService;
import com.astray.insightflow.retrieval.domain.EvidenceRecord;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CitationGuardTool {

    private final ToolCallLogService toolCallLogService;
    private final EvidenceRecordRepository evidenceRecordRepository;

    public CitationGuardTool(ToolCallLogService toolCallLogService,
                             EvidenceRecordRepository evidenceRecordRepository) {
        this.toolCallLogService = toolCallLogService;
        this.evidenceRecordRepository = evidenceRecordRepository;
    }

    public ClaimValidationResult validateClaims(String taskId,
                                                String nodeName,
                                                List<VerifiedClaim> claims,
                                                List<Evidence> evidences) {
        Set<String> allowedIds = evidenceIds(taskId, evidences);
        List<VerifiedClaim> sanitized = claims == null ? new ArrayList<>() : new ArrayList<>(claims);
        int invalidCitationCount = 0;
        int unsupportedClaimCount = 0;

        for (VerifiedClaim claim : sanitized) {
            int before = sizeOf(claim.getSupportingEvidenceIds()) + sizeOf(claim.getConflictingEvidenceIds());
            claim.setSupportingEvidenceIds(retainAllowed(claim.getSupportingEvidenceIds(), allowedIds));
            claim.setConflictingEvidenceIds(retainAllowed(claim.getConflictingEvidenceIds(), allowedIds));
            int after = claim.getSupportingEvidenceIds().size() + claim.getConflictingEvidenceIds().size();
            invalidCitationCount += before - after;

            if (claim.getSupportingEvidenceIds().isEmpty()) {
                unsupportedClaimCount++;
                claim.setLowConfidence(true);
                if (claim.getConflictingEvidenceIds().isEmpty()) {
                    claim.setStatus(VerifiedClaimStatus.INSUFFICIENT);
                }
            }
            if (!claim.getConflictingEvidenceIds().isEmpty()) {
                claim.setStatus(VerifiedClaimStatus.CONFLICTING);
            }
        }

        ClaimValidationResult result = new ClaimValidationResult(
                sanitized,
                invalidCitationCount,
                unsupportedClaimCount,
                allowedIds.size()
        );
        log(taskId, nodeName, "claims", result.metrics());
        return result;
    }

    public ReportValidationResult validateReportDraft(String taskId,
                                                      String nodeName,
                                                      ReportDraft draft,
                                                      List<Evidence> evidences) {
        Set<String> allowedIds = evidenceIds(taskId, evidences);
        ReportDraft sanitized = draft == null ? new ReportDraft() : draft;
        int invalidCitationCount = 0;
        int unsupportedSectionCount = 0;

        for (ReportSection section : sanitized.getSections()) {
            int before = sizeOf(section.getEvidenceIds());
            List<String> retained = retainAllowed(section.getEvidenceIds(), allowedIds);
            section.setEvidenceIds(retained);
            invalidCitationCount += before - retained.size();
            if (retained.isEmpty()) {
                unsupportedSectionCount++;
                section.setLowConfidence(true);
            }
        }

        if (invalidCitationCount > 0 || unsupportedSectionCount > 0) {
            sanitized.setConfidenceNote(appendGuardNote(
                    sanitized.getConfidenceNote(),
                    invalidCitationCount,
                    unsupportedSectionCount
            ));
        }

        ReportValidationResult result = new ReportValidationResult(
                sanitized,
                invalidCitationCount,
                unsupportedSectionCount,
                allowedIds.size()
        );
        log(taskId, nodeName, "report", result.metrics());
        return result;
    }

    private void log(String taskId, String nodeName, String target, Map<String, Object> metrics) {
        Instant startedAt = Instant.now();
        toolCallLogService.logSuccess(
                taskId,
                nodeName,
                "CitationGuardTool",
                startedAt,
                Map.of("target", target),
                metrics,
                metrics
        );
    }

    private Set<String> evidenceIds(String taskId, List<Evidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return Set.of();
        }
        Set<String> inMemoryIds = evidences.stream()
                .map(Evidence::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> persistedTaskIds = evidenceRecordRepository.findByTaskIdOrderByScoreDescCreatedAtAsc(taskId).stream()
                .map(EvidenceRecord::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        inMemoryIds.retainAll(persistedTaskIds);
        return inMemoryIds;
    }

    private List<String> retainAllowed(List<String> ids, Set<String> allowedIds) {
        if (ids == null || ids.isEmpty() || allowedIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> retained = new LinkedHashSet<>();
        for (String id : ids) {
            if (StringUtils.hasText(id) && allowedIds.contains(id)) {
                retained.add(id);
            }
        }
        return new ArrayList<>(retained);
    }

    private int sizeOf(List<String> ids) {
        return ids == null ? 0 : ids.size();
    }

    private String appendGuardNote(String currentNote, int invalidCitationCount, int unsupportedSectionCount) {
        String guardNote = "Citation guard: removed " + invalidCitationCount
                + " invalid citation(s); marked " + unsupportedSectionCount
                + " unsupported section(s) low confidence.";
        if (!StringUtils.hasText(currentNote)) {
            return guardNote;
        }
        if (currentNote.contains("Citation guard:")) {
            return currentNote;
        }
        return currentNote + " " + guardNote;
    }

    public record ClaimValidationResult(List<VerifiedClaim> claims,
                                        int invalidCitationCount,
                                        int unsupportedClaimCount,
                                        int allowedEvidenceCount) {

        public Map<String, Object> metrics() {
            return Map.of(
                    "invalidCitationCount", invalidCitationCount,
                    "unsupportedClaimCount", unsupportedClaimCount,
                    "allowedEvidenceCount", allowedEvidenceCount
            );
        }
    }

    public record ReportValidationResult(ReportDraft draft,
                                         int invalidCitationCount,
                                         int unsupportedSectionCount,
                                         int allowedEvidenceCount) {

        public Map<String, Object> metrics() {
            return Map.of(
                    "invalidCitationCount", invalidCitationCount,
                    "unsupportedSectionCount", unsupportedSectionCount,
                    "allowedEvidenceCount", allowedEvidenceCount
            );
        }
    }
}
