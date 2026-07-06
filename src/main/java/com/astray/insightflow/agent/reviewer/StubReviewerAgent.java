package com.astray.insightflow.agent.reviewer;

import com.astray.insightflow.agent.verifier.VerifiedClaim;
import com.astray.insightflow.agent.verifier.VerifiedClaimStatus;
import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.agent.writer.ReportSection;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.retrieval.model.Evidence;

import java.util.ArrayList;
import java.util.List;

public class StubReviewerAgent implements ReviewerAgent {

    private final JsonUtils jsonUtils;

    public StubReviewerAgent(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    @Override
    public ReviewResult review(String query, String language, String reportJson, String claimsJson, String evidenceJson, int loopCount) {
        ReportDraft reportDraft = jsonUtils.fromJson(reportJson, ReportDraft.class);
        ClaimListWrapper claimsWrapper = jsonUtils.fromJson(claimsJson, ClaimListWrapper.class);
        EvidenceListWrapper evidenceWrapper = jsonUtils.fromJson(evidenceJson, EvidenceListWrapper.class);

        int missingCitationCount = 0;
        int lowConfidenceSectionCount = 0;
        for (ReportSection section : reportDraft.getSections()) {
            if (section.getEvidenceIds() == null || section.getEvidenceIds().isEmpty()) {
                missingCitationCount++;
            }
            if (section.isLowConfidence()) {
                lowConfidenceSectionCount++;
            }
        }

        int unsupportedClaimCount = (int) claimsWrapper.getItems().stream()
                .filter(claim -> claim.isLowConfidence()
                        || claim.getSupportingEvidenceIds().isEmpty()
                        || claim.getStatus() == VerifiedClaimStatus.CONFLICTING
                        || claim.getStatus() == VerifiedClaimStatus.INSUFFICIENT)
                .count();

        ReviewResult result = new ReviewResult();
        result.setMissingCitationCount(missingCitationCount);
        result.setUnsupportedClaimCount(unsupportedClaimCount);
        result.setLowConfidenceSectionCount(lowConfidenceSectionCount);

        List<ReviewFinding> findings = new ArrayList<>();
        if (missingCitationCount > 0) {
            findings.add(new ReviewFinding("MISSING_CITATION", "存在无引用章节，需要补充依据。", true));
        }
        if (unsupportedClaimCount > 0) {
            findings.add(new ReviewFinding("LOW_SUPPORT", "存在低置信度或支持不足的 claim。", unsupportedClaimCount > evidenceWrapper.getItems().size()));
        }

        result.setFindings(findings);

        if (loopCount > 2) {
            result.setApproved(true);
            result.setMarkLowConfidence(true);
            result.setRerunFrom(ReviewRerunTarget.NONE);
            result.setSummary("已达到最大回退次数，当前以低置信度结果结束。");
            return result;
        }

        if (missingCitationCount > 0 && evidenceWrapper.getItems().size() < 2) {
            result.setApproved(false);
            result.setRerunFrom(ReviewRerunTarget.RETRIEVAL);
            result.setSummary("引用覆盖不足，建议回到检索阶段补证。");
            return result;
        }

        if (unsupportedClaimCount > 0 && loopCount == 0) {
            result.setApproved(false);
            result.setRerunFrom(ReviewRerunTarget.VERIFY);
            result.setSummary("存在低置信度 claim，建议重新验证后再审查。");
            return result;
        }

        result.setApproved(true);
        result.setRerunFrom(ReviewRerunTarget.NONE);
        result.setSummary(findings.isEmpty() ? "审查通过。" : "审查通过，但已记录风险提示。");
        return result;
    }

    public static class ClaimListWrapper {

        private List<VerifiedClaim> items = new ArrayList<>();

        public List<VerifiedClaim> getItems() {
            return items;
        }

        public void setItems(List<VerifiedClaim> items) {
            this.items = items == null ? new ArrayList<>() : items;
        }
    }

    public static class EvidenceListWrapper {

        private List<Evidence> items = new ArrayList<>();

        public List<Evidence> getItems() {
            return items;
        }

        public void setItems(List<Evidence> items) {
            this.items = items == null ? new ArrayList<>() : items;
        }
    }
}
