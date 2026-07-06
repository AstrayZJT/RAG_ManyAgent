package com.astray.insightflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(Search search, Webpage webpage) {

    public record Search(int maxResults) {
    }

    public record Webpage(int maxCharacters) {
    }
}
