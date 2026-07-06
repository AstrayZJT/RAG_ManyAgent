package com.astray.insightflow.observe.api;

import java.time.Instant;
import java.util.Map;

public record CheckpointLogEntry(
        String checkpointId,
        String nodeName,
        String nextNodeName,
        String saveMode,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> stateSummary,
        Map<String, Object> stateSnapshot
) {
}
