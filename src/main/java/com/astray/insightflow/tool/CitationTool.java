package com.astray.insightflow.tool;

import com.astray.insightflow.agent.verifier.VerifiedClaim;
import com.astray.insightflow.observe.service.ToolCallLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                        .filter(evidence -> evidence.getSnippet() != null && claim.getClaimText() != null
                                && (claim.getClaimText().contains(evidence.getTitle())
                                || evidence.getSnippet().contains(claim.getDimension())))
                        .map(Evidence::getId)
                        .distinct()
                        .collect(Collectors.toList());
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
}
