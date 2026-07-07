package com.astray.insightflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(Search search, Webpage webpage) {

    public record Search(int maxResults,
                         int maxExternalResultsPerQuery,
                         int maxExternalPages,
                         int timeoutMs,
                         String userAgent) {
    }

    public record Webpage(int maxCharacters, int minCharacters) {
    }
}
