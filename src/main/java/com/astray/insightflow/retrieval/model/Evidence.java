package com.astray.insightflow.retrieval.model;

import com.astray.insightflow.retrieval.domain.EvidenceSourceType;

import java.io.Serializable;

public class Evidence implements Serializable {

    private String id;
    private String title;
    private String snippet;
    private String url;
    private EvidenceSourceType sourceType;
    private String documentId;
    private String chunkId;
    private double score;

    public Evidence() {
    }

    public Evidence(String id, String title, String snippet, String url, EvidenceSourceType sourceType,
                    String documentId, String chunkId, double score) {
        this.id = id;
        this.title = title;
        this.snippet = snippet;
        this.url = url;
        this.sourceType = sourceType;
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.score = score;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public EvidenceSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(EvidenceSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
