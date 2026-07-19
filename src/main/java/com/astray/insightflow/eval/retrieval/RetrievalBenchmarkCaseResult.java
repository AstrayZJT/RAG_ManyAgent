package com.astray.insightflow.eval.retrieval;

import java.util.List;

public record RetrievalBenchmarkCaseResult(
        String id,
        String category,
        String query,
        String expectedContentContains,
        Integer firstRelevantRank,
        double reciprocalRank,
        List<String> retrievedChunkIds
) {
}
