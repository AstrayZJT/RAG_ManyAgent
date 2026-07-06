package com.astray.insightflow.agent.verifier;

import java.io.Serializable;

public class VerifyDecision implements Serializable {

    private boolean readyForWrite;
    private boolean rerunRetrieval;
    private String reason;
    private double supportRatio;
    private int conflictCount;
    private boolean lowConfidenceMode;

    public boolean isReadyForWrite() {
        return readyForWrite;
    }

    public void setReadyForWrite(boolean readyForWrite) {
        this.readyForWrite = readyForWrite;
    }

    public boolean isRerunRetrieval() {
        return rerunRetrieval;
    }

    public void setRerunRetrieval(boolean rerunRetrieval) {
        this.rerunRetrieval = rerunRetrieval;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public double getSupportRatio() {
        return supportRatio;
    }

    public void setSupportRatio(double supportRatio) {
        this.supportRatio = supportRatio;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }

    public boolean isLowConfidenceMode() {
        return lowConfidenceMode;
    }

    public void setLowConfidenceMode(boolean lowConfidenceMode) {
        this.lowConfidenceMode = lowConfidenceMode;
    }
}
