package com.astray.insightflow.eval.retrieval;

import java.time.Instant;

public record RetrievalBenchmarkComparisonResponse(
        boolean hybridAvailable,
        boolean rerankAvailable,
        RetrievalBenchmarkResponse keyword,
        RetrievalBenchmarkResponse hybrid,
        RetrievalBenchmarkResponse hybridRerank,
        Instant evaluatedAt
) {
}
