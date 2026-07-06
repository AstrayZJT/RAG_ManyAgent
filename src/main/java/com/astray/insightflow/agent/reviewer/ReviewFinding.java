package com.astray.insightflow.agent.reviewer;

import java.io.Serializable;

public class ReviewFinding implements Serializable {

    private String type;
    private String message;
    private boolean evidenceRequired;

    public ReviewFinding() {
    }

    public ReviewFinding(String type, String message, boolean evidenceRequired) {
        this.type = type;
        this.message = message;
        this.evidenceRequired = evidenceRequired;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isEvidenceRequired() {
        return evidenceRequired;
    }

    public void setEvidenceRequired(boolean evidenceRequired) {
        this.evidenceRequired = evidenceRequired;
    }
}
