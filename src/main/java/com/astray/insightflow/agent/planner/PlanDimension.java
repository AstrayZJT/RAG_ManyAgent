package com.astray.insightflow.agent.planner;

import java.io.Serializable;

public class PlanDimension implements Serializable {

    private String name;
    private String rationale;

    public PlanDimension() {
    }

    public PlanDimension(String name, String rationale) {
        this.name = name;
        this.rationale = rationale;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }
}
