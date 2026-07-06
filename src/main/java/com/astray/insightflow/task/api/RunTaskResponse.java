package com.astray.insightflow.task.api;

public record RunTaskResponse(
        String taskId,
        String status,
        String message
) {
}
