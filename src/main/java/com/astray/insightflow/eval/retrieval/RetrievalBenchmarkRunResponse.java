package com.astray.insightflow.eval.retrieval;

import com.astray.insightflow.eval.domain.RetrievalBenchmarkRun;
import com.astray.insightflow.retrieval.model.RetrievalMode;

import java.time.Instant;

public record RetrievalBenchmarkRunResponse(
        String id,
        RetrievalMode mode,
        boolean vectorEnabled,
        boolean rerankEnabled,
        RetrievalMetrics metrics,
        double traceabilityCoverage,
        Instant createdAt
) {

    public static RetrievalBenchmarkRunResponse from(RetrievalBenchmarkRun run) {
        return new RetrievalBenchmarkRunResponse(
                run.getId(),
                run.getMode(),
                run.isVectorEnabled(),
                run.isRerankEnabled(),
                new RetrievalMetrics(
                        run.getCaseCount(),
                        run.getHitAt1(),
                        run.getHitAt3(),
                        run.getHitAt5(),
                        run.getMrr()
                ),
                run.getTraceabilityCoverage(),
                run.getCreatedAt()
        );
    }
}
