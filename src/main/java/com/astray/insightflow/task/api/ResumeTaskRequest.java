package com.astray.insightflow.task.api;

import java.util.Map;

public record ResumeTaskRequest(
        String checkpointId,
        Map<String, Object> statePatch
) {
}
