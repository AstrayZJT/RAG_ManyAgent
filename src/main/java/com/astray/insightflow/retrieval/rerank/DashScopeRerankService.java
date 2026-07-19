package com.astray.insightflow.retrieval.rerank;

import com.astray.insightflow.config.RerankProperties;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.model.EvidenceScoreBreakdown;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "rag.rerank", name = "enabled", havingValue = "true")
public class DashScopeRerankService implements RerankService {

    private final RerankProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DashScopeRerankService(RerankProperties properties, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException("rag.rerank.api-key is required when rerank is enabled");
        }
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int candidateCount() {
        return properties.candidateCount();
    }

    @Override
    public List<Evidence> rerank(List<String> queries, List<Evidence> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Evidence> limitedCandidates = candidates.stream()
                .limit(properties.candidateCount())
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.modelName());
        body.put("input", Map.of(
                "query", String.join(" ; ", queries),
                "documents", limitedCandidates.stream().map(this::documentText).toList()
        ));
        body.put("parameters", Map.of(
                "return_documents", false,
                "top_n", Math.min(properties.topN(), limitedCandidates.size())
        ));

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(properties.baseUrl()))
                    .timeout(properties.timeout())
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize rerank request", exception);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new IllegalStateException("Rerank request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rerank request was interrupted", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Rerank request returned HTTP " + response.statusCode());
        }

        try {
            JsonNode results = objectMapper.readTree(response.body()).path("output").path("results");
            List<Evidence> reranked = new ArrayList<>();
            for (JsonNode result : results) {
                int index = result.path("index").asInt(-1);
                if (index < 0 || index >= limitedCandidates.size()) {
                    continue;
                }
                Evidence evidence = limitedCandidates.get(index);
                double rerankScore = result.path("relevance_score").asDouble(0D);
                applyRerankScore(evidence, rerankScore);
                reranked.add(evidence);
            }
            if (reranked.isEmpty()) {
                throw new IllegalStateException("Rerank response did not contain valid candidates");
            }
            return reranked;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse rerank response", exception);
        }
    }

    private String documentText(Evidence evidence) {
        String title = evidence.getTitle() == null ? "" : evidence.getTitle();
        String snippet = evidence.getSnippet() == null ? "" : evidence.getSnippet();
        return title + "\n" + snippet;
    }

    private void applyRerankScore(Evidence evidence, double rerankScore) {
        EvidenceScoreBreakdown current = evidence.getScoreBreakdown();
        evidence.setScore(rerankScore);
        if (current == null) {
            evidence.setScoreBreakdown(new EvidenceScoreBreakdown(
                    0D, 0D, 0D, 0D, null, null, null, 0D, rerankScore, "gte_rerank"
            ));
            return;
        }
        evidence.setScoreBreakdown(new EvidenceScoreBreakdown(
                current.lexicalScore(),
                current.termFrequencyScore(),
                current.coverageScore(),
                current.titleBoost(),
                current.vectorScore(),
                current.lexicalRank(),
                current.vectorRank(),
                current.fusedScore(),
                rerankScore,
                "weighted_rrf_gte_rerank"
        ));
    }
}
