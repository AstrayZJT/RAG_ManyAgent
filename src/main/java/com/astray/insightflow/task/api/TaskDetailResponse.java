package com.astray.insightflow.task.api;

import com.astray.insightflow.agent.planner.PlanResult;

import java.time.Instant;

public record TaskDetailResponse(
        String id,
        String query,
        String language,
        String status,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        long evidenceCount,
        boolean reportAvailable,
        PlanResult plan
) {
}
