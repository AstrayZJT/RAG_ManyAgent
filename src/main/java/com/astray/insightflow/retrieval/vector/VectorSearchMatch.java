package com.astray.insightflow.retrieval.vector;

public record VectorSearchMatch(
        String chunkId,
        double score
) {
}
