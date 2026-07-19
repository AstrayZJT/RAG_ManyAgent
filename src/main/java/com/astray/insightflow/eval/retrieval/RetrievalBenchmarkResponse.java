package com.astray.insightflow.eval.retrieval;

import com.astray.insightflow.retrieval.model.RetrievalMode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RetrievalBenchmarkResponse(
        RetrievalMode mode,
        boolean vectorEnabled,
        boolean rerankEnabled,
        RetrievalMetrics metrics,
        Map<String, RetrievalMetrics> categoryMetrics,
        double traceabilityCoverage,
        List<RetrievalBenchmarkCaseResult> cases,
        Instant evaluatedAt
) {
}
