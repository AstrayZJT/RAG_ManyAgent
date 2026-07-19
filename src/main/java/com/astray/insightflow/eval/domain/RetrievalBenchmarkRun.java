package com.astray.insightflow.eval.domain;

import com.astray.insightflow.retrieval.model.RetrievalMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "retrieval_benchmark_run")
public class RetrievalBenchmarkRun {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RetrievalMode mode;

    @Column(nullable = false)
    private boolean vectorEnabled;

    private Boolean rerankEnabled;

    @Column(nullable = false)
    private int caseCount;

    @Column(nullable = false)
    private double hitAt1;

    @Column(nullable = false)
    private double hitAt3;

    @Column(nullable = false)
    private double hitAt5;

    @Column(nullable = false)
    private double mrr;

    @Column(nullable = false)
    private double traceabilityCoverage;

    @Column(nullable = false, columnDefinition = "text")
    private String resultJson;

    @Column(nullable = false)
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RetrievalMode getMode() {
        return mode;
    }

    public void setMode(RetrievalMode mode) {
        this.mode = mode;
    }

    public boolean isVectorEnabled() {
        return vectorEnabled;
    }

    public void setVectorEnabled(boolean vectorEnabled) {
        this.vectorEnabled = vectorEnabled;
    }

    public boolean isRerankEnabled() {
        return Boolean.TRUE.equals(rerankEnabled);
    }

    public void setRerankEnabled(boolean rerankEnabled) {
        this.rerankEnabled = rerankEnabled;
    }

    public int getCaseCount() {
        return caseCount;
    }

    public void setCaseCount(int caseCount) {
        this.caseCount = caseCount;
    }

    public double getHitAt1() {
        return hitAt1;
    }

    public void setHitAt1(double hitAt1) {
        this.hitAt1 = hitAt1;
    }

    public double getHitAt3() {
        return hitAt3;
    }

    public void setHitAt3(double hitAt3) {
        this.hitAt3 = hitAt3;
    }

    public double getHitAt5() {
        return hitAt5;
    }

    public void setHitAt5(double hitAt5) {
        this.hitAt5 = hitAt5;
    }

    public double getMrr() {
        return mrr;
    }

    public void setMrr(double mrr) {
        this.mrr = mrr;
    }

    public double getTraceabilityCoverage() {
        return traceabilityCoverage;
    }

    public void setTraceabilityCoverage(double traceabilityCoverage) {
        this.traceabilityCoverage = traceabilityCoverage;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
