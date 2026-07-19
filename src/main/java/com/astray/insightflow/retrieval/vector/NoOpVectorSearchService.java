package com.astray.insightflow.retrieval.vector;

import com.astray.insightflow.knowledge.domain.DocumentChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "rag.embedding", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpVectorSearchService implements VectorSearchService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void replaceDocumentChunks(List<String> staleChunkIds,
                                      List<DocumentChunk> newChunks,
                                      String collectionName) {
    }

    @Override
    public List<VectorSearchMatch> search(List<String> queries, int maxResults, String collectionName) {
        return List.of();
    }
}
