package com.astray.insightflow.common.util;

import com.astray.insightflow.agent.verifier.VerifiedClaim;

import java.util.List;

public final class MetricsUtils {

    private MetricsUtils() {
    }

    public static int estimateTokens(String... fragments) {
        int characters = 0;
        for (String fragment : fragments) {
            if (fragment != null) {
                characters += fragment.length();
            }
        }
        return Math.max(1, characters / 4);
    }

    public static double citationCoverage(List<VerifiedClaim> claims) {
        if (claims == null || claims.isEmpty()) {
            return 0D;
        }
        long cited = claims.stream().filter(claim -> !claim.getSupportingEvidenceIds().isEmpty()).count();
        return cited / (double) claims.size();
    }
}
