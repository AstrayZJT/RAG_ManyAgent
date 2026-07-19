package com.astray.insightflow.retrieval.rerank;

import com.astray.insightflow.config.RerankProperties;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashScopeRerankServiceTests {

    @Test
    void appliesProviderOrderAndScoresToCandidates() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/rerank", exchange -> {
            byte[] response = """
                    {"output":{"results":[
                      {"index":1,"relevance_score":0.91},
                      {"index":0,"relevance_score":0.42}
                    ]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            RerankProperties properties = new RerankProperties(
                    true,
                    "http://localhost:" + server.getAddress().getPort() + "/rerank",
                    "test-key",
                    "test-reranker",
                    10,
                    5,
                    Duration.ofSeconds(5)
            );
            DashScopeRerankService service = new DashScopeRerankService(properties, new ObjectMapper());

            List<Evidence> result = service.rerank(
                    List.of("range anxiety"),
                    List.of(evidence("e-1", "battery safety"), evidence("e-2", "range extender"))
            );

            assertEquals(List.of("e-2", "e-1"), result.stream().map(Evidence::getId).toList());
            assertEquals(0.91D, result.getFirst().getScore());
            assertEquals(0.91D, result.getFirst().getScoreBreakdown().rerankScore());
            assertEquals("gte_rerank", result.getFirst().getScoreBreakdown().strategy());
        } finally {
            server.stop(0);
        }
    }

    private Evidence evidence(String id, String snippet) {
        return new Evidence(
                id,
                "title",
                snippet,
                null,
                EvidenceSourceType.INTERNAL,
                "doc-1",
                "chunk-" + id,
                0.5D
        );
    }
}
