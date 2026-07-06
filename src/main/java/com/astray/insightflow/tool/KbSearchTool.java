package com.astray.insightflow.tool;

import com.astray.insightflow.observe.service.ToolCallLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.service.InternalRetrievalService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class KbSearchTool {

    private final InternalRetrievalService internalRetrievalService;
    private final ToolCallLogService toolCallLogService;

    public KbSearchTool(InternalRetrievalService internalRetrievalService, ToolCallLogService toolCallLogService) {
        this.internalRetrievalService = internalRetrievalService;
        this.toolCallLogService = toolCallLogService;
    }

    @Tool("Searches internal knowledge chunks for relevant evidence")
    public List<Evidence> search(String taskId, String nodeName, List<String> queries) {
        Instant startedAt = Instant.now();
        Map<String, Object> input = Map.of("queries", queries);
        try {
            List<Evidence> result = internalRetrievalService.search(taskId, queries);
            toolCallLogService.logSuccess(taskId, nodeName, "KbSearchTool", startedAt, input, result, Map.of(
                    "retrievalCount", result.size()
            ));
            return result;
        } catch (Exception exception) {
            toolCallLogService.logFailure(taskId, nodeName, "KbSearchTool", startedAt, input, exception);
            throw exception;
        }
    }

    public List<Evidence> search(String taskId, List<String> queries) {
        return search(taskId, "unknown", queries);
    }
}
