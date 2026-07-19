package com.astray.insightflow.retrieval.vector;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class PgvectorEmbeddingStoreIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("insightflow_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Test
    void storesSearchesAndFiltersEmbeddingsByCollection() {
        EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getFirstMappedPort())
                .database(POSTGRES.getDatabaseName())
                .user(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .table("test_embeddings")
                .dimension(3)
                .createTable(true)
                .useIndex(false)
                .indexListSize(10)
                .build();

        store.addAll(
                java.util.List.of(
                        "00000000-0000-0000-0000-000000000001",
                        "00000000-0000-0000-0000-000000000002"
                ),
                java.util.List.of(
                        Embedding.from(new float[]{1F, 0F, 0F}),
                        Embedding.from(new float[]{0F, 1F, 0F})
                ),
                java.util.List.of(
                        TextSegment.from("relevant", new Metadata().put("collectionName", "benchmark")),
                        TextSegment.from("other collection", new Metadata().put("collectionName", "default"))
                )
        );

        var result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[]{1F, 0F, 0F}))
                .maxResults(5)
                .minScore(0D)
                .filter(metadataKey("collectionName").isEqualTo("benchmark"))
                .build());

        assertEquals(1, result.matches().size());
        assertEquals("relevant", result.matches().getFirst().embedded().text());
    }
}
