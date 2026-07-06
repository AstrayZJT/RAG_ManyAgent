package com.astray.insightflow.observe.api;

import java.time.Instant;
import java.util.Map;

public record AgentRunLogEntry(
        String nodeName,
        String status,
        String message,
        Instant startedAt,
        Instant endedAt,
        long latencyMs,
        Map<String, Object> metrics
) {
}
