package com.astray.insightflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
public record OpenAiChatModelProperties(
        String baseUrl,
        String apiKey,
        String modelName,
        Double temperature,
        Integer maxCompletionTokens,
        Duration timeout,
        Integer maxRetries,
        Boolean strictJsonSchema,
        boolean logRequests,
        boolean logResponses
) {
}
