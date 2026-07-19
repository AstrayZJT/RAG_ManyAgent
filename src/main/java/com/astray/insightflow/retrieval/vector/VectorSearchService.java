package com.astray.insightflow.retrieval.vector;

import com.astray.insightflow.knowledge.domain.DocumentChunk;

import java.util.List;

public interface VectorSearchService {

    boolean isEnabled();

    void replaceDocumentChunks(List<String> staleChunkIds,
                               List<DocumentChunk> newChunks,
                               String collectionName);

    List<VectorSearchMatch> search(List<String> queries, int maxResults, String collectionName);
}
