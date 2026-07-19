package com.astray.insightflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        String knowledgePath,
        Pgvector pgvector,
        Chunking chunking,
        Embedding embedding,
        Retrieval retrieval
) {

    public RagProperties {
        if (knowledgePath == null || knowledgePath.isBlank()) {
            knowledgePath = "knowledge";
        }
        if (chunking == null) {
            chunking = Chunking.defaults();
        }
        if (embedding == null) {
            embedding = Embedding.defaults();
        }
        if (retrieval == null) {
            retrieval = Retrieval.defaults();
        }
    }

    public record Pgvector(
            String host,
            int port,
            String database,
            String username,
            String password,
            String table,
            boolean useIndex,
            int indexListSize
    ) {

        public Pgvector {
            if (port <= 0) {
                port = 5432;
            }
            if (table == null || table.isBlank()) {
                table = "knowledge_embeddings";
            }
            if (indexListSize <= 0) {
                indexListSize = 100;
            }
        }
    }

    public record Chunking(
            int maxLength,
            int overlap
    ) {

        static final int DEFAULT_MAX_LENGTH = 800;
        static final int DEFAULT_OVERLAP = 120;

        public Chunking {
            if (maxLength <= 0) {
                maxLength = DEFAULT_MAX_LENGTH;
            }
            if (overlap < 0) {
                overlap = 0;
            }
            if (overlap >= maxLength) {
                overlap = Math.max(0, maxLength / 5);
            }
        }

        public static Chunking defaults() {
            return new Chunking(DEFAULT_MAX_LENGTH, DEFAULT_OVERLAP);
        }
    }

    public record Embedding(
            boolean enabled,
            String baseUrl,
            String apiKey,
            String modelName,
            int dimension,
            Duration timeout,
            int maxRetries,
            int maxResults,
            double minScore
    ) {

        public Embedding {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
            }
            if (modelName == null || modelName.isBlank()) {
                modelName = "text-embedding-v4";
            }
            if (dimension <= 0) {
                dimension = 1024;
            }
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                timeout = Duration.ofSeconds(60);
            }
            if (maxRetries < 0) {
                maxRetries = 2;
            }
            if (maxResults <= 0) {
                maxResults = 20;
            }
            if (minScore < 0D || minScore > 1D) {
                minScore = 0.55D;
            }
        }

        public static Embedding defaults() {
            return new Embedding(false, null, null, null, 1024, Duration.ofSeconds(60), 2, 20, 0.55D);
        }
    }

    public record Retrieval(
            double keywordWeight,
            double vectorWeight,
            int rrfK,
            int candidateMultiplier
    ) {

        public Retrieval {
            if (keywordWeight < 0D) {
                keywordWeight = 0D;
            }
            if (vectorWeight < 0D) {
                vectorWeight = 0D;
            }
            if (keywordWeight == 0D && vectorWeight == 0D) {
                keywordWeight = 0.45D;
                vectorWeight = 0.55D;
            }
            double totalWeight = keywordWeight + vectorWeight;
            keywordWeight /= totalWeight;
            vectorWeight /= totalWeight;
            if (rrfK <= 0) {
                rrfK = 60;
            }
            if (candidateMultiplier <= 0) {
                candidateMultiplier = 4;
            }
        }

        public static Retrieval defaults() {
            return new Retrieval(0.45D, 0.55D, 60, 4);
        }
    }
}
