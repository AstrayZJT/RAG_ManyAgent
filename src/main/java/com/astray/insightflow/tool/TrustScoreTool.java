package com.astray.insightflow.tool;

import com.astray.insightflow.observe.service.ToolCallLogService;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TrustScoreTool {

    private final ToolCallLogService toolCallLogService;

    public TrustScoreTool(ToolCallLogService toolCallLogService) {
        this.toolCallLogService = toolCallLogService;
    }

    @Tool("Scores evidence source trustworthiness")
    public Map<String, Double> scoreBatch(String taskId, String nodeName, List<Evidence> evidences) {
        Instant startedAt = Instant.now();
        try {
            Map<String, Double> scores = new LinkedHashMap<>();
            for (Evidence evidence : evidences) {
                scores.put(evidence.getId(), score(evidence));
            }
            toolCallLogService.logSuccess(taskId, nodeName, "TrustScoreTool", startedAt,
                    Map.of("evidenceCount", evidences.size()), scores, Map.of("evidenceCount", evidences.size()));
            return scores;
        } catch (Exception exception) {
            toolCallLogService.logFailure(taskId, nodeName, "TrustScoreTool", startedAt,
                    Map.of("evidenceCount", evidences.size()), exception);
            throw exception;
        }
    }

    public double score(Evidence evidence) {
        if (evidence.getSourceType() == EvidenceSourceType.INTERNAL) {
            return 0.92D;
        }
        if (evidence.getUrl() != null && (evidence.getUrl().contains(".gov") || evidence.getUrl().contains(".edu"))) {
            return 0.88D;
        }
        return 0.68D;
    }
}
