package com.astray.insightflow.eval.retrieval;

public record RetrievalBenchmarkCorpusResponse(
        String collectionName,
        int documentCount,
        int indexedCount,
        boolean forceReindexed
) {
}
