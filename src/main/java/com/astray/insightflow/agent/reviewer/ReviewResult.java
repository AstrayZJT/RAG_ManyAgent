package com.astray.insightflow.agent.reviewer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ReviewResult implements Serializable {

    private boolean approved;
    private ReviewRerunTarget rerunFrom = ReviewRerunTarget.NONE;
    private String summary;
    private List<ReviewFinding> findings = new ArrayList<>();
    private int missingCitationCount;
    private int unsupportedClaimCount;
    private int lowConfidenceSectionCount;
    private boolean markLowConfidence;

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public ReviewRerunTarget getRerunFrom() {
        return rerunFrom;
    }

    public void setRerunFrom(ReviewRerunTarget rerunFrom) {
        this.rerunFrom = rerunFrom;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<ReviewFinding> getFindings() {
        return findings;
    }

    public void setFindings(List<ReviewFinding> findings) {
        this.findings = findings == null ? new ArrayList<>() : findings;
    }

    public int getMissingCitationCount() {
        return missingCitationCount;
    }

    public void setMissingCitationCount(int missingCitationCount) {
        this.missingCitationCount = missingCitationCount;
    }

    public int getUnsupportedClaimCount() {
        return unsupportedClaimCount;
    }

    public void setUnsupportedClaimCount(int unsupportedClaimCount) {
        this.unsupportedClaimCount = unsupportedClaimCount;
    }

    public int getLowConfidenceSectionCount() {
        return lowConfidenceSectionCount;
    }

    public void setLowConfidenceSectionCount(int lowConfidenceSectionCount) {
        this.lowConfidenceSectionCount = lowConfidenceSectionCount;
    }

    public boolean isMarkLowConfidence() {
        return markLowConfidence;
    }

    public void setMarkLowConfidence(boolean markLowConfidence) {
        this.markLowConfidence = markLowConfidence;
    }
}
