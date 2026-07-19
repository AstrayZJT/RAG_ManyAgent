package com.astray.insightflow.eval.retrieval;

public record RetrievalBenchmarkCase(
        String id,
        String category,
        String query,
        String expectedContentContains
) {
}
