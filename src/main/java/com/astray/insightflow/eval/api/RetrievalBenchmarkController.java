package com.astray.insightflow.eval.api;

import com.astray.insightflow.eval.retrieval.RetrievalBenchmarkResponse;
import com.astray.insightflow.eval.retrieval.RetrievalBenchmarkComparisonResponse;
import com.astray.insightflow.eval.retrieval.RetrievalBenchmarkCorpusResponse;
import com.astray.insightflow.eval.retrieval.RetrievalBenchmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations/retrieval")
public class RetrievalBenchmarkController {

    private final RetrievalBenchmarkService retrievalBenchmarkService;

    public RetrievalBenchmarkController(RetrievalBenchmarkService retrievalBenchmarkService) {
        this.retrievalBenchmarkService = retrievalBenchmarkService;
    }

    @PostMapping
    public ResponseEntity<RetrievalBenchmarkResponse> runBenchmark() {
        return ResponseEntity.ok(retrievalBenchmarkService.run());
    }

    @PostMapping("/compare")
    public ResponseEntity<RetrievalBenchmarkComparisonResponse> compare() {
        return ResponseEntity.ok(retrievalBenchmarkService.compare());
    }

    @PostMapping("/corpus")
    public ResponseEntity<RetrievalBenchmarkCorpusResponse> prepareCorpus(
            @RequestParam(value = "forceReindex", defaultValue = "false") boolean forceReindex) {
        return ResponseEntity.ok(retrievalBenchmarkService.prepareCorpus(forceReindex));
    }

    @GetMapping("/runs")
    public ResponseEntity<java.util.List<com.astray.insightflow.eval.retrieval.RetrievalBenchmarkRunResponse>> history(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(retrievalBenchmarkService.history(limit));
    }
}
