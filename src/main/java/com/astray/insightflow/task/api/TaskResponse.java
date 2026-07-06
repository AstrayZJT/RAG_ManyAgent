package com.astray.insightflow.task.api;

import com.astray.insightflow.task.domain.ResearchTask;

import java.time.Instant;

public record TaskResponse(
        String id,
        String query,
        String language,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static TaskResponse from(ResearchTask task) {
        return new TaskResponse(
                task.getId(),
                task.getQueryText(),
                task.getLanguage(),
                task.getStatus().name(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
