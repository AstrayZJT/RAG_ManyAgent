package com.astray.insightflow.retrieval.vector;

import com.astray.insightflow.config.RagProperties;
import com.astray.insightflow.knowledge.domain.DocumentChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@ConditionalOnProperty(prefix = "rag.embedding", name = "enabled", havingValue = "true")
public class PgvectorVectorSearchService implements VectorSearchService {

    private static final String CHUNK_ID = "chunkId";
    private static final String DOCUMENT_ID = "documentId";
    private static final String CHUNK_INDEX = "chunkIndex";
    private static final String START_OFFSET = "startOffset";
    private static final String END_OFFSET = "endOffset";
    private static final String CONTENT_HASH = "contentHash";
    private static final String COLLECTION_NAME = "collectionName";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int configuredMaxResults;
    private final double minScore;

    public PgvectorVectorSearchService(RagProperties ragProperties) {
        RagProperties.Embedding embedding = ragProperties.embedding();
        RagProperties.Pgvector pgvector = ragProperties.pgvector();
        validateConfiguration(embedding, pgvector);

        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl(embedding.baseUrl())
                .apiKey(embedding.apiKey())
                .modelName(embedding.modelName())
                .dimensions(embedding.dimension())
                .timeout(embedding.timeout())
                .maxRetries(embedding.maxRetries())
                .build();
        this.embeddingStore = PgVectorEmbeddingStore.builder()
                .host(pgvector.host())
                .port(pgvector.port())
                .database(pgvector.database())
                .user(pgvector.username())
                .password(pgvector.password())
                .table(pgvector.table())
                .dimension(embedding.dimension())
                .createTable(true)
                .useIndex(pgvector.useIndex())
                .indexListSize(pgvector.indexListSize())
                .build();
        this.configuredMaxResults = embedding.maxResults();
        this.minScore = embedding.minScore();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void replaceDocumentChunks(List<String> staleChunkIds,
                                      List<DocumentChunk> newChunks,
                                      String collectionName) {
        List<String> newChunkIds = newChunks.stream().map(DocumentChunk::getId).toList();
        if (!newChunks.isEmpty()) {
            List<TextSegment> segments = newChunks.stream()
                    .map(chunk -> toSegment(chunk, collectionName))
                    .toList();
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            if (embeddings.size() != newChunks.size()) {
                throw new IllegalStateException("Embedding model returned an unexpected vector count");
            }
            try {
                embeddingStore.addAll(newChunkIds, embeddings, segments);
            } catch (RuntimeException exception) {
                removeQuietly(newChunkIds);
                throw exception;
            }
        }

        Set<String> staleIds = new LinkedHashSet<>(staleChunkIds == null ? List.of() : staleChunkIds);
        staleIds.removeAll(newChunkIds);
        if (!staleIds.isEmpty()) {
            embeddingStore.removeAll(staleIds);
        }
    }

    @Override
    public List<VectorSearchMatch> search(List<String> queries, int maxResults, String collectionName) {
        int resultLimit = Math.max(1, Math.min(configuredMaxResults, maxResults));
        Map<String, Double> bestScores = new LinkedHashMap<>();
        Filter collectionFilter = StringUtils.hasText(collectionName)
                ? metadataKey(COLLECTION_NAME).isEqualTo(collectionName)
                : null;
        for (String query : normalizeQueries(queries)) {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(resultLimit)
                            .minScore(minScore)
                            .filter(collectionFilter)
                            .build()
            ).matches();
            for (EmbeddingMatch<TextSegment> match : matches) {
                String chunkId = resolveChunkId(match);
                if (StringUtils.hasText(chunkId)) {
                    bestScores.merge(chunkId, match.score(), Math::max);
                }
            }
        }
        return bestScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(resultLimit)
                .map(entry -> new VectorSearchMatch(entry.getKey(), entry.getValue()))
                .toList();
    }

    private TextSegment toSegment(DocumentChunk chunk, String collectionName) {
        Metadata metadata = new Metadata()
                .put(CHUNK_ID, chunk.getId())
                .put(DOCUMENT_ID, chunk.getDocumentId())
                .put(CHUNK_INDEX, chunk.getChunkIndex())
                .put(START_OFFSET, chunk.getStartOffset())
                .put(END_OFFSET, chunk.getEndOffset());
        if (StringUtils.hasText(collectionName)) {
            metadata.put(COLLECTION_NAME, collectionName);
        }
        if (StringUtils.hasText(chunk.getContentHash())) {
            metadata.put(CONTENT_HASH, chunk.getContentHash());
        }
        return TextSegment.from(chunk.getContent(), metadata);
    }

    private String resolveChunkId(EmbeddingMatch<TextSegment> match) {
        if (match.embedded() != null && match.embedded().metadata() != null) {
            String metadataChunkId = match.embedded().metadata().getString(CHUNK_ID);
            if (StringUtils.hasText(metadataChunkId)) {
                return metadataChunkId;
            }
        }
        return match.embeddingId();
    }

    private List<String> normalizeQueries(List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String query : queries) {
            if (StringUtils.hasText(query)) {
                normalized.add(query.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    private void removeQuietly(List<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        try {
            embeddingStore.removeAll(ids);
        } catch (RuntimeException ignored) {
        }
    }

    private void validateConfiguration(RagProperties.Embedding embedding, RagProperties.Pgvector pgvector) {
        if (!StringUtils.hasText(embedding.apiKey())) {
            throw new IllegalStateException("rag.embedding.api-key is required when embeddings are enabled");
        }
        if (pgvector == null
                || !StringUtils.hasText(pgvector.host())
                || !StringUtils.hasText(pgvector.database())
                || !StringUtils.hasText(pgvector.username())
                || !StringUtils.hasText(pgvector.table())) {
            throw new IllegalStateException("rag.pgvector connection settings are required when embeddings are enabled");
        }
    }
}
