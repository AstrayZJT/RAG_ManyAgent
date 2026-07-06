package com.astray.insightflow.task.api;

import jakarta.validation.constraints.NotBlank;

public record CreateTaskRequest(
        @NotBlank(message = "query must not be blank")
        String query,
        String language
) {
}
