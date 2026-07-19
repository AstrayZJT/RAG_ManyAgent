package com.astray.insightflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "rag.rerank")
public record RerankProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String modelName,
        int candidateCount,
        int topN,
        Duration timeout
) {

    public RerankProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        }
        if (modelName == null || modelName.isBlank()) {
            modelName = "gte-rerank-v2";
        }
        if (candidateCount <= 0) {
            candidateCount = 20;
        }
        if (topN <= 0) {
            topN = 5;
        }
        if (topN > candidateCount) {
            topN = candidateCount;
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofSeconds(60);
        }
    }
}
