package com.astray.insightflow.task.api;

import java.util.Map;

public record RerunTaskRequest(
        Map<String, Object> statePatch
) {
}
