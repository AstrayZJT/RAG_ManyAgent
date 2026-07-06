package com.astray.insightflow.agent.verifier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VerifiedClaim implements Serializable {

    private String id;
    private String dimension;
    private String claimText;
    private String rationale;
    private List<String> supportingEvidenceIds = new ArrayList<>();
    private List<String> conflictingEvidenceIds = new ArrayList<>();
    private double confidenceScore;
    private VerifiedClaimStatus status = VerifiedClaimStatus.INSUFFICIENT;
    private boolean lowConfidence;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getClaimText() {
        return claimText;
    }

    public void setClaimText(String claimText) {
        this.claimText = claimText;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public List<String> getSupportingEvidenceIds() {
        return supportingEvidenceIds;
    }

    public void setSupportingEvidenceIds(List<String> supportingEvidenceIds) {
        this.supportingEvidenceIds = supportingEvidenceIds == null ? new ArrayList<>() : supportingEvidenceIds;
    }

    public List<String> getConflictingEvidenceIds() {
        return conflictingEvidenceIds;
    }

    public void setConflictingEvidenceIds(List<String> conflictingEvidenceIds) {
        this.conflictingEvidenceIds = conflictingEvidenceIds == null ? new ArrayList<>() : conflictingEvidenceIds;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public VerifiedClaimStatus getStatus() {
        return status;
    }

    public void setStatus(VerifiedClaimStatus status) {
        this.status = status;
    }

    public boolean isLowConfidence() {
        return lowConfidence;
    }

    public void setLowConfidence(boolean lowConfidence) {
        this.lowConfidence = lowConfidence;
    }
}
