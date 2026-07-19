package com.astray.insightflow.eval.retrieval;

import com.astray.insightflow.eval.domain.RetrievalBenchmarkRun;
import com.astray.insightflow.eval.persistence.RetrievalBenchmarkRunRepository;
import com.astray.insightflow.knowledge.domain.KnowledgeDocumentStatus;
import com.astray.insightflow.knowledge.service.KnowledgeDocumentService;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.model.RetrievalMode;
import com.astray.insightflow.retrieval.rerank.RerankService;
import com.astray.insightflow.retrieval.service.InternalRetrievalService;
import com.astray.insightflow.retrieval.vector.VectorSearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class RetrievalBenchmarkService {

    private static final String BENCHMARK_RESOURCE = "eval/retrieval-benchmark.json";
    private static final String CORPUS_RESOURCE = "eval/retrieval-benchmark-corpus.json";
    public static final String BENCHMARK_COLLECTION = "retrieval-benchmark-v1";

    private final InternalRetrievalService internalRetrievalService;
    private final VectorSearchService vectorSearchService;
    private final RetrievalMetricsCalculator metricsCalculator;
    private final ObjectMapper objectMapper;
    private final RetrievalBenchmarkRunRepository benchmarkRunRepository;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final RerankService rerankService;

    public RetrievalBenchmarkService(InternalRetrievalService internalRetrievalService,
                                     VectorSearchService vectorSearchService,
                                     RetrievalMetricsCalculator metricsCalculator,
                                     ObjectMapper objectMapper,
                                     RetrievalBenchmarkRunRepository benchmarkRunRepository,
                                     KnowledgeDocumentService knowledgeDocumentService,
                                     RerankService rerankService) {
        this.internalRetrievalService = internalRetrievalService;
        this.vectorSearchService = vectorSearchService;
        this.metricsCalculator = metricsCalculator;
        this.objectMapper = objectMapper;
        this.benchmarkRunRepository = benchmarkRunRepository;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.rerankService = rerankService;
    }

    public RetrievalBenchmarkResponse run() {
        return run(RetrievalMode.HYBRID);
    }

    public RetrievalBenchmarkComparisonResponse compare() {
        RetrievalBenchmarkResponse keyword = run(RetrievalMode.KEYWORD);
        RetrievalBenchmarkResponse hybrid = run(RetrievalMode.HYBRID);
        RetrievalBenchmarkResponse hybridRerank = run(RetrievalMode.HYBRID_RERANK);
        return new RetrievalBenchmarkComparisonResponse(
                vectorSearchService.isEnabled(),
                rerankService.isEnabled(),
                keyword,
                hybrid,
                hybridRerank,
                Instant.now()
        );
    }

    public RetrievalBenchmarkResponse run(RetrievalMode mode) {
        prepareCorpus(false);
        List<RetrievalBenchmarkCase> benchmarkCases = loadCases();
        List<RetrievalBenchmarkCaseResult> caseResults = new ArrayList<>();
        List<Integer> firstRelevantRanks = new ArrayList<>();
        Map<String, List<Integer>> categoryRanks = new LinkedHashMap<>();
        int totalRetrievedEvidence = 0;
        int traceableRetrievedEvidence = 0;
        for (RetrievalBenchmarkCase benchmarkCase : benchmarkCases) {
            List<Evidence> ranked = internalRetrievalService.rank(
                    List.of(benchmarkCase.query()),
                    mode,
                    BENCHMARK_COLLECTION
            );
            totalRetrievedEvidence += ranked.size();
            traceableRetrievedEvidence += (int) ranked.stream()
                    .filter(this::isTraceable)
                    .count();
            Integer firstRelevantRank = findFirstRelevantRank(ranked, benchmarkCase.expectedContentContains());
            firstRelevantRanks.add(firstRelevantRank);
            categoryRanks.computeIfAbsent(benchmarkCase.category(), ignored -> new ArrayList<>())
                    .add(firstRelevantRank);
            caseResults.add(new RetrievalBenchmarkCaseResult(
                    benchmarkCase.id(),
                    benchmarkCase.category(),
                    benchmarkCase.query(),
                    benchmarkCase.expectedContentContains(),
                    firstRelevantRank,
                    firstRelevantRank == null ? 0D : 1D / firstRelevantRank,
                    ranked.stream().map(Evidence::getChunkId).toList()
            ));
        }
        RetrievalBenchmarkResponse response = new RetrievalBenchmarkResponse(
                mode,
                mode != RetrievalMode.KEYWORD && vectorSearchService.isEnabled(),
                mode == RetrievalMode.HYBRID_RERANK && rerankService.isEnabled(),
                metricsCalculator.calculate(firstRelevantRanks),
                categoryMetrics(categoryRanks),
                totalRetrievedEvidence == 0
                        ? 0D
                        : traceableRetrievedEvidence / (double) totalRetrievedEvidence,
                caseResults,
                Instant.now()
        );
        saveRun(response);
        return response;
    }

    private Map<String, RetrievalMetrics> categoryMetrics(Map<String, List<Integer>> categoryRanks) {
        Map<String, RetrievalMetrics> result = new LinkedHashMap<>();
        categoryRanks.forEach((category, ranks) -> result.put(category, metricsCalculator.calculate(ranks)));
        return result;
    }

    public List<RetrievalBenchmarkRunResponse> history(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return benchmarkRunRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit)).stream()
                .map(RetrievalBenchmarkRunResponse::from)
                .toList();
    }

    public RetrievalBenchmarkCorpusResponse prepareCorpus(boolean forceReindex) {
        List<RetrievalBenchmarkDocument> documents = loadCorpus();
        int indexedCount = 0;
        for (RetrievalBenchmarkDocument document : documents) {
            try {
                if (knowledgeDocumentService.importText(
                        document.filename(),
                        document.content(),
                        BENCHMARK_COLLECTION,
                        forceReindex
                ).getStatus() == KnowledgeDocumentStatus.INDEXED) {
                    indexedCount++;
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to prepare benchmark document: " + document.filename(), exception);
            }
        }
        return new RetrievalBenchmarkCorpusResponse(
                BENCHMARK_COLLECTION,
                documents.size(),
                indexedCount,
                forceReindex
        );
    }

    private void saveRun(RetrievalBenchmarkResponse response) {
        RetrievalMetrics metrics = response.metrics();
        RetrievalBenchmarkRun run = new RetrievalBenchmarkRun();
        run.setId(UUID.randomUUID().toString());
        run.setMode(response.mode());
        run.setVectorEnabled(response.vectorEnabled());
        run.setRerankEnabled(response.rerankEnabled());
        run.setCaseCount(metrics.caseCount());
        run.setHitAt1(metrics.hitAt1());
        run.setHitAt3(metrics.hitAt3());
        run.setHitAt5(metrics.hitAt5());
        run.setMrr(metrics.mrr());
        run.setTraceabilityCoverage(response.traceabilityCoverage());
        try {
            run.setResultJson(objectMapper.writeValueAsString(response));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize retrieval benchmark result", exception);
        }
        run.setCreatedAt(response.evaluatedAt());
        benchmarkRunRepository.save(run);
    }

    private boolean isTraceable(Evidence evidence) {
        return evidence.getDocumentId() != null
                && evidence.getChunkId() != null
                && evidence.getDocumentHash() != null
                && evidence.getChunkHash() != null
                && evidence.getStartOffset() != null
                && evidence.getEndOffset() != null;
    }

    private Integer findFirstRelevantRank(List<Evidence> ranked, String expectedContentContains) {
        String expected = normalize(expectedContentContains);
        for (int index = 0; index < ranked.size(); index++) {
            if (normalize(ranked.get(index).getSnippet()).contains(expected)) {
                return index + 1;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private List<RetrievalBenchmarkCase> loadCases() {
        return readList(BENCHMARK_RESOURCE, new TypeReference<>() {
        });
    }

    private List<RetrievalBenchmarkDocument> loadCorpus() {
        return readList(CORPUS_RESOURCE, new TypeReference<>() {
        });
    }

    private <T> List<T> readList(String resourcePath, TypeReference<List<T>> typeReference) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, typeReference);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load retrieval benchmark resource: " + resourcePath, exception);
        }
    }
}
