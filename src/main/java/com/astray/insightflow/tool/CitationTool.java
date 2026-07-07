package com.astray.insightflow.tool;

import com.astray.insightflow.agent.verifier.VerifiedClaim;
import com.astray.insightflow.observe.service.ToolCallLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CitationTool {

    private final ToolCallLogService toolCallLogService;

    public CitationTool(ToolCallLogService toolCallLogService) {
        this.toolCallLogService = toolCallLogService;
    }

    @Tool("Binds claims to matching evidence ids")
    public List<VerifiedClaim> attach(String taskId, String nodeName, List<VerifiedClaim> claims, List<Evidence> evidences) {
        Instant startedAt = Instant.now();
        try {
            for (VerifiedClaim claim : claims) {
                if (!claim.getSupportingEvidenceIds().isEmpty()) {
                    continue;
                }
                List<String> matches = evidences.stream()
                        .map(evidence -> Map.entry(evidence, matchScore(claim, evidence)))
                        .filter(entry -> entry.getValue() > 0D)
                        .sorted(Map.Entry.<Evidence, Double>comparingByValue(Comparator.reverseOrder()))
                        .limit(3)
                        .map(entry -> entry.getKey().getId())
                        .toList();
                claim.setSupportingEvidenceIds(matches);
            }
            toolCallLogService.logSuccess(taskId, nodeName, "CitationTool", startedAt,
                    Map.of("claimCount", claims.size(), "evidenceCount", evidences.size()),
                    claims, Map.of("citationCoverage", coverage(claims)));
            return claims;
        } catch (Exception exception) {
            toolCallLogService.logFailure(taskId, nodeName, "CitationTool", startedAt,
                    Map.of("claimCount", claims.size(), "evidenceCount", evidences.size()), exception);
            throw exception;
        }
    }

    private double coverage(List<VerifiedClaim> claims) {
        if (claims.isEmpty()) {
            return 0D;
        }
        long cited = claims.stream().filter(claim -> !claim.getSupportingEvidenceIds().isEmpty()).count();
        return cited / (double) claims.size();
    }

    private double matchScore(VerifiedClaim claim, Evidence evidence) {
        String claimText = normalize(claim.getClaimText()) + " " + normalize(claim.getRationale()) + " " + normalize(claim.getDimension());
        String evidenceText = normalize(evidence.getTitle()) + " " + normalize(evidence.getSnippet());
        if (!StringUtils.hasText(claimText.trim()) || !StringUtils.hasText(evidenceText.trim())) {
            return 0D;
        }

        Set<String> claimTerms = expandTerms(claimText);
        if (claimTerms.isEmpty()) {
            return 0D;
        }

        double score = 0D;
        for (String term : claimTerms) {
            if (term.length() < 2) {
                continue;
            }
            if (evidenceText.contains(term)) {
                score += containsChinese(term) ? 1.0D : 0.6D;
            }
        }
        if (StringUtils.hasText(claim.getDimension()) && evidenceText.contains(normalize(claim.getDimension()))) {
            score += 0.8D;
        }
        return score;
    }

    private Set<String> expandTerms(String text) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : normalize(text).split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            terms.add(token);
            if (containsChinese(token) && token.length() > 1) {
                for (int index = 0; index < token.length() - 1; index++) {
                    terms.add(token.substring(index, index + 2));
                }
            }
        }
        return terms;
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean containsChinese(String token) {
        return token.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }
}
