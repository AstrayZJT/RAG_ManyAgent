package com.astray.insightflow.tool;

import com.astray.insightflow.observe.service.ToolCallLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class RerankTool {

    private final ToolCallLogService toolCallLogService;

    public RerankTool(ToolCallLogService toolCallLogService) {
        this.toolCallLogService = toolCallLogService;
    }

    @Tool("Sorts evidence by descending relevance score")
    public List<Evidence> rerank(String taskId, String nodeName, List<Evidence> evidences) {
        Instant startedAt = Instant.now();
        try {
            List<Evidence> sorted = new ArrayList<>(evidences);
            sorted.sort(Comparator.comparingDouble(Evidence::getScore).reversed());
            toolCallLogService.logSuccess(taskId, nodeName, "RerankTool", startedAt,
                    Map.of("inputCount", evidences.size()), sorted, Map.of("retrievalCount", sorted.size()));
            return sorted;
        } catch (Exception exception) {
            toolCallLogService.logFailure(taskId, nodeName, "RerankTool", startedAt,
                    Map.of("inputCount", evidences.size()), exception);
            throw exception;
        }
    }

    public List<Evidence> rerank(List<Evidence> evidences) {
        return rerank("unknown", "unknown", evidences);
    }
}
