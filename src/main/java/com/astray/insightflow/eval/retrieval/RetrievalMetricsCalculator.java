package com.astray.insightflow.eval.retrieval;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RetrievalMetricsCalculator {

    public RetrievalMetrics calculate(List<Integer> firstRelevantRanks) {
        if (firstRelevantRanks == null || firstRelevantRanks.isEmpty()) {
            return new RetrievalMetrics(0, 0D, 0D, 0D, 0D);
        }
        int caseCount = firstRelevantRanks.size();
        long hitAt1 = firstRelevantRanks.stream().filter(rank -> rank != null && rank <= 1).count();
        long hitAt3 = firstRelevantRanks.stream().filter(rank -> rank != null && rank <= 3).count();
        long hitAt5 = firstRelevantRanks.stream().filter(rank -> rank != null && rank <= 5).count();
        double reciprocalRankSum = firstRelevantRanks.stream()
                .filter(rank -> rank != null && rank > 0)
                .mapToDouble(rank -> 1D / rank)
                .sum();
        return new RetrievalMetrics(
                caseCount,
                hitAt1 / (double) caseCount,
                hitAt3 / (double) caseCount,
                hitAt5 / (double) caseCount,
                reciprocalRankSum / caseCount
        );
    }
}
