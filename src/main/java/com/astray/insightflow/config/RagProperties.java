package com.astray.insightflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        String knowledgePath,
        Pgvector pgvector
) {

    public record Pgvector(
            String host,
            int port,
            String database,
            String username,
            String password,
            String table
    ) {
    }
}
