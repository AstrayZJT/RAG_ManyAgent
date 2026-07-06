package com.astray.insightflow.agent.planner;

import java.io.Serializable;

public class FactField implements Serializable {

    private String name;
    private String description;

    public FactField() {
    }

    public FactField(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
