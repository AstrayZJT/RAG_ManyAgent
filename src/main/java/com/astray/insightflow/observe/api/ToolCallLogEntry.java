package com.astray.insightflow.observe.api;

import java.time.Instant;
import java.util.Map;

public record ToolCallLogEntry(
        String nodeName,
        String toolName,
        String status,
        Instant startedAt,
        Instant endedAt,
        long latencyMs,
        Map<String, Object> metrics
) {
}
