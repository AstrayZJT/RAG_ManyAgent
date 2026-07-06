package com.astray.insightflow.agent.planner;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PlanResult implements Serializable {

    private String objectiveSummary;
    private List<PlanDimension> dimensions = new ArrayList<>();
    private List<String> subQueries = new ArrayList<>();
    private String retrievalStrategy;
    private List<FactField> factSchema = new ArrayList<>();
    private boolean needExternalSearch;

    public PlanResult() {
    }

    public String getObjectiveSummary() {
        return objectiveSummary;
    }

    public void setObjectiveSummary(String objectiveSummary) {
        this.objectiveSummary = objectiveSummary;
    }

    public List<PlanDimension> getDimensions() {
        return dimensions;
    }

    public void setDimensions(List<PlanDimension> dimensions) {
        this.dimensions = dimensions == null ? new ArrayList<>() : dimensions;
    }

    public List<String> getSubQueries() {
        return subQueries;
    }

    public void setSubQueries(List<String> subQueries) {
        this.subQueries = subQueries == null ? new ArrayList<>() : subQueries;
    }

    public String getRetrievalStrategy() {
        return retrievalStrategy;
    }

    public void setRetrievalStrategy(String retrievalStrategy) {
        this.retrievalStrategy = retrievalStrategy;
    }

    public List<FactField> getFactSchema() {
        return factSchema;
    }

    public void setFactSchema(List<FactField> factSchema) {
        this.factSchema = factSchema == null ? new ArrayList<>() : factSchema;
    }

    public boolean isNeedExternalSearch() {
        return needExternalSearch;
    }

    public void setNeedExternalSearch(boolean needExternalSearch) {
        this.needExternalSearch = needExternalSearch;
    }
}
