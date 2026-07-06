package com.astray.insightflow.report.api;

import com.astray.insightflow.agent.writer.ReportDraft;

import java.time.Instant;

public record ReportResponse(
        String taskId,
        String title,
        String markdown,
        ReportDraft draft,
        Instant updatedAt
) {
}
