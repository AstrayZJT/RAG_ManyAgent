package com.astray.insightflow.agent.extractor;

import com.astray.insightflow.retrieval.domain.EvidenceSourceType;

import java.io.Serializable;

public class ExtractedFact implements Serializable {

    private String id;
    private String dimension;
    private String subject;
    private String attribute;
    private String value;
    private String normalizedValue;
    private String evidenceId;
    private String evidenceSnippet;
    private EvidenceSourceType sourceType;
    private double confidence;

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

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getNormalizedValue() {
        return normalizedValue;
    }

    public void setNormalizedValue(String normalizedValue) {
        this.normalizedValue = normalizedValue;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String evidenceId) {
        this.evidenceId = evidenceId;
    }

    public String getEvidenceSnippet() {
        return evidenceSnippet;
    }

    public void setEvidenceSnippet(String evidenceSnippet) {
        this.evidenceSnippet = evidenceSnippet;
    }

    public EvidenceSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(EvidenceSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
