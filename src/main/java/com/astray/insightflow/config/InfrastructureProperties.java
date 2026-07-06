package com.astray.insightflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infrastructure")
public record InfrastructureProperties(
        Redis redis,
        Minio minio,
        Rabbitmq rabbitmq
) {

    public record Redis(boolean enabled, String host, int port) {
    }

    public record Minio(boolean enabled, String endpoint, String bucket) {
    }

    public record Rabbitmq(boolean enabled, String host, int port) {
    }
}
