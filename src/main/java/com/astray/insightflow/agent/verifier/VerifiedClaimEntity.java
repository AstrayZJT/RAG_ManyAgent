package com.astray.insightflow.agent.verifier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "verified_claim")
public class VerifiedClaimEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String taskId;

    @Column(nullable = false, length = 128)
    private String dimensionName;

    @Column(nullable = false, length = 4000)
    private String claimText;

    @Lob
    @Column(nullable = false)
    private String supportEvidenceJson;

    @Lob
    @Column(nullable = false)
    private String conflictEvidenceJson;

    @Column(nullable = false)
    private double confidenceScore;

    @Column(nullable = false, length = 32)
    private String claimStatus;

    @Column(length = 4000)
    private String rationale;

    @Column(nullable = false)
    private boolean lowConfidence;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDimensionName() {
        return dimensionName;
    }

    public void setDimensionName(String dimensionName) {
        this.dimensionName = dimensionName;
    }

    public String getClaimText() {
        return claimText;
    }

    public void setClaimText(String claimText) {
        this.claimText = claimText;
    }

    public String getSupportEvidenceJson() {
        return supportEvidenceJson;
    }

    public void setSupportEvidenceJson(String supportEvidenceJson) {
        this.supportEvidenceJson = supportEvidenceJson;
    }

    public String getConflictEvidenceJson() {
        return conflictEvidenceJson;
    }

    public void setConflictEvidenceJson(String conflictEvidenceJson) {
        this.conflictEvidenceJson = conflictEvidenceJson;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getClaimStatus() {
        return claimStatus;
    }

    public void setClaimStatus(String claimStatus) {
        this.claimStatus = claimStatus;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public boolean isLowConfidence() {
        return lowConfidence;
    }

    public void setLowConfidence(boolean lowConfidence) {
        this.lowConfidence = lowConfidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
