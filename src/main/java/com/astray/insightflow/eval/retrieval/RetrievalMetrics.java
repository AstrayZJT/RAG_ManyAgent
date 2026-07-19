package com.astray.insightflow.eval.retrieval;

public record RetrievalMetrics(
        int caseCount,
        double hitAt1,
        double hitAt3,
        double hitAt5,
        double mrr
) {
}
