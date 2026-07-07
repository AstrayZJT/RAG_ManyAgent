package com.astray.insightflow.graph.checkpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "graph_checkpoint_meta")
public class GraphCheckpointMeta {

    @Id
    @Column(nullable = false, length = 64)
    private String checkpointId;

    @Column(nullable = false, length = 64)
    private String taskId;

    @Column(length = 128)
    private String nodeName;

    @Column(length = 128)
    private String nextNodeName;

    @Column(nullable = false, length = 32)
    private String saveMode;

    @Column(nullable = false, columnDefinition = "text")
    private String stateJson;

    @Column(columnDefinition = "text")
    private String stateSummaryJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNextNodeName() {
        return nextNodeName;
    }

    public void setNextNodeName(String nextNodeName) {
        this.nextNodeName = nextNodeName;
    }

    public String getSaveMode() {
        return saveMode;
    }

    public void setSaveMode(String saveMode) {
        this.saveMode = saveMode;
    }

    public String getStateJson() {
        return stateJson;
    }

    public void setStateJson(String stateJson) {
        this.stateJson = stateJson;
    }

    public String getStateSummaryJson() {
        return stateSummaryJson;
    }

    public void setStateSummaryJson(String stateSummaryJson) {
        this.stateSummaryJson = stateSummaryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
