package com.astray.insightflow.agent.writer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ReportSection implements Serializable {

    private String heading;
    private String content;
    private List<String> evidenceIds = new ArrayList<>();
    private boolean lowConfidence;

    public ReportSection() {
    }

    public ReportSection(String heading, String content, List<String> evidenceIds) {
        this.heading = heading;
        this.content = content;
        this.evidenceIds = evidenceIds == null ? new ArrayList<>() : evidenceIds;
    }

    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getEvidenceIds() {
        return evidenceIds;
    }

    public void setEvidenceIds(List<String> evidenceIds) {
        this.evidenceIds = evidenceIds == null ? new ArrayList<>() : evidenceIds;
    }

    public boolean isLowConfidence() {
        return lowConfidence;
    }

    public void setLowConfidence(boolean lowConfidence) {
        this.lowConfidence = lowConfidence;
    }
}
