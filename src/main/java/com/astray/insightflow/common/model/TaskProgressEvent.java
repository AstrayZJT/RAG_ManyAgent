package com.astray.insightflow.common.model;

import java.time.Instant;
import java.util.Map;

public record TaskProgressEvent(
        Instant timestamp,
        String stage,
        String status,
        String message,
        Map<String, Object> payload
) {
}
