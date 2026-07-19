package com.astray.insightflow.retrieval.service;

public record SearchHit(
        String title,
        String normalizedUrl,
        String snippet,
        String query
) {
}
