package com.astray.insightflow.agent.writer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ReportDraft implements Serializable {

    private String title;
    private String executiveSummary;
    private List<ReportSection> sections = new ArrayList<>();
    private String closingSummary;
    private String confidenceNote;

    public ReportDraft() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExecutiveSummary() {
        return executiveSummary;
    }

    public void setExecutiveSummary(String executiveSummary) {
        this.executiveSummary = executiveSummary;
    }

    public List<ReportSection> getSections() {
        return sections;
    }

    public void setSections(List<ReportSection> sections) {
        this.sections = sections == null ? new ArrayList<>() : sections;
    }

    public String getClosingSummary() {
        return closingSummary;
    }

    public void setClosingSummary(String closingSummary) {
        this.closingSummary = closingSummary;
    }

    public String getConfidenceNote() {
        return confidenceNote;
    }

    public void setConfidenceNote(String confidenceNote) {
        this.confidenceNote = confidenceNote;
    }
}
