package com.astray.insightflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
public record OpenAiChatModelProperties(
        String baseUrl,
        String apiKey,
        String modelName,
        boolean logRequests,
        boolean logResponses
) {
}
