package com.astray.insightflow.tool;

import com.astray.insightflow.agent.verifier.VerifiedClaim;
import com.astray.insightflow.agent.verifier.VerifiedClaimStatus;
import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.agent.writer.ReportSection;
import com.astray.insightflow.observe.service.ToolCallLogService;
import com.astray.insightflow.retrieval.domain.EvidenceRecord;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CitationGuardToolTests {

    private final ToolCallLogService toolCallLogService = mock(ToolCallLogService.class);
    private final EvidenceRecordRepository evidenceRecordRepository = mock(EvidenceRecordRepository.class);
    private final CitationGuardTool citationGuardTool = new CitationGuardTool(toolCallLogService, evidenceRecordRepository);

    @Test
    void validateClaimsDropsUnknownEvidenceIdsAndMarksUnsupportedClaims() {
        when(evidenceRecordRepository.findByTaskIdOrderByScoreDescCreatedAtAsc("task-1"))
                .thenReturn(List.of(record("e-1")));
        VerifiedClaim claim = new VerifiedClaim();
        claim.setId("claim-1");
        claim.setStatus(VerifiedClaimStatus.SUPPORTED);
        claim.setSupportingEvidenceIds(List.of("e-1", "missing"));
        claim.setConflictingEvidenceIds(List.of("missing-conflict"));

        CitationGuardTool.ClaimValidationResult result = citationGuardTool.validateClaims(
                "task-1",
                "verify",
                List.of(claim),
                List.of(evidence("e-1"))
        );

        VerifiedClaim sanitized = result.claims().get(0);
        assertEquals(List.of("e-1"), sanitized.getSupportingEvidenceIds());
        assertEquals(List.of(), sanitized.getConflictingEvidenceIds());
        assertEquals(2, result.invalidCitationCount());
        assertEquals(0, result.unsupportedClaimCount());
        verify(toolCallLogService).logSuccess(anyString(), anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void validateReportDraftUsesEvidenceWhitelistAndMarksUnsupportedSections() {
        when(evidenceRecordRepository.findByTaskIdOrderByScoreDescCreatedAtAsc("task-1"))
                .thenReturn(List.of(record("e-1")));
        ReportSection supported = new ReportSection("Supported", "content", List.of("e-1", "missing"));
        ReportSection unsupported = new ReportSection("Unsupported", "content", List.of("missing-2"));
        ReportDraft draft = new ReportDraft();
        draft.setSections(List.of(supported, unsupported));
        draft.setConfidenceNote("Initial note.");

        CitationGuardTool.ReportValidationResult result = citationGuardTool.validateReportDraft(
                "task-1",
                "write",
                draft,
                List.of(evidence("e-1"))
        );

        assertEquals(List.of("e-1"), result.draft().getSections().get(0).getEvidenceIds());
        assertEquals(List.of(), result.draft().getSections().get(1).getEvidenceIds());
        assertTrue(result.draft().getSections().get(1).isLowConfidence());
        assertTrue(result.draft().getConfidenceNote().contains("Citation guard"));
        assertEquals(2, result.invalidCitationCount());
        assertEquals(1, result.unsupportedSectionCount());
    }

    private Evidence evidence(String id) {
        return new Evidence(
                id,
                "title",
                "snippet",
                null,
                EvidenceSourceType.INTERNAL,
                "doc-1",
                "chunk-1",
                0.9D
        );
    }

    private EvidenceRecord record(String id) {
        EvidenceRecord record = new EvidenceRecord();
        record.setId(id);
        return record;
    }
}
