package com.astray.insightflow.eval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "evaluation_record")
public class EvaluationRecord {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String taskId;

    @Column(nullable = false)
    private int retrievalHitCount;

    @Column(nullable = false)
    private int internalEvidenceCount;

    @Column(nullable = false)
    private int externalEvidenceCount;

    @Column(nullable = false)
    private int mergedEvidenceCount;

    @Column(nullable = false)
    private double retrievalHitRate;

    @Column(nullable = false)
    private double citationCoverage;

    @Column(nullable = false)
    private double claimSupportRate;

    @Column(nullable = false)
    private double reportCompleteness;

    @Column(nullable = false)
    private double overallScore;

    @Column(nullable = false)
    private int lowConfidenceSectionCount;

    @Column(nullable = false)
    private boolean reviewApproved;

    @Column(nullable = false, columnDefinition = "text")
    private String detailsJson;

    @Column(nullable = false)
    private Instant createdAt;

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

    public int getRetrievalHitCount() {
        return retrievalHitCount;
    }

    public void setRetrievalHitCount(int retrievalHitCount) {
        this.retrievalHitCount = retrievalHitCount;
    }

    public int getInternalEvidenceCount() {
        return internalEvidenceCount;
    }

    public void setInternalEvidenceCount(int internalEvidenceCount) {
        this.internalEvidenceCount = internalEvidenceCount;
    }

    public int getExternalEvidenceCount() {
        return externalEvidenceCount;
    }

    public void setExternalEvidenceCount(int externalEvidenceCount) {
        this.externalEvidenceCount = externalEvidenceCount;
    }

    public int getMergedEvidenceCount() {
        return mergedEvidenceCount;
    }

    public void setMergedEvidenceCount(int mergedEvidenceCount) {
        this.mergedEvidenceCount = mergedEvidenceCount;
    }

    public double getRetrievalHitRate() {
        return retrievalHitRate;
    }

    public void setRetrievalHitRate(double retrievalHitRate) {
        this.retrievalHitRate = retrievalHitRate;
    }

    public double getCitationCoverage() {
        return citationCoverage;
    }

    public void setCitationCoverage(double citationCoverage) {
        this.citationCoverage = citationCoverage;
    }

    public double getClaimSupportRate() {
        return claimSupportRate;
    }

    public void setClaimSupportRate(double claimSupportRate) {
        this.claimSupportRate = claimSupportRate;
    }

    public double getReportCompleteness() {
        return reportCompleteness;
    }

    public void setReportCompleteness(double reportCompleteness) {
        this.reportCompleteness = reportCompleteness;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public int getLowConfidenceSectionCount() {
        return lowConfidenceSectionCount;
    }

    public void setLowConfidenceSectionCount(int lowConfidenceSectionCount) {
        this.lowConfidenceSectionCount = lowConfidenceSectionCount;
    }

    public boolean isReviewApproved() {
        return reviewApproved;
    }

    public void setReviewApproved(boolean reviewApproved) {
        this.reviewApproved = reviewApproved;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
