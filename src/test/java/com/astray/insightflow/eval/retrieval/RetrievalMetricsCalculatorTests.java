package com.astray.insightflow.eval.retrieval;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrievalMetricsCalculatorTests {

    @Test
    void calculatesHitAtKAndMrrFromFirstRelevantRanks() {
        RetrievalMetrics metrics = new RetrievalMetricsCalculator().calculate(Arrays.asList(1, 2, 5, null));

        assertEquals(4, metrics.caseCount());
        assertEquals(0.25D, metrics.hitAt1());
        assertEquals(0.50D, metrics.hitAt3());
        assertEquals(0.75D, metrics.hitAt5());
        assertEquals(0.425D, metrics.mrr(), 0.000_001D);
    }
}
