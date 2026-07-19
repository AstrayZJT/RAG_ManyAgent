package com.astray.insightflow.retrieval.model;

import java.io.Serializable;

public record EvidenceScoreBreakdown(
        double lexicalScore,
        double termFrequencyScore,
        double coverageScore,
        double titleBoost,
        Double vectorScore,
        Integer lexicalRank,
        Integer vectorRank,
        double fusedScore,
        Double rerankScore,
        String strategy
) implements Serializable {
}
