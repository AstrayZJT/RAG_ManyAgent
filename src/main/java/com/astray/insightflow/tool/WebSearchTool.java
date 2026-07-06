package com.astray.insightflow.tool;

import com.astray.insightflow.observe.service.ToolCallLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.service.ExternalRetrievalService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class WebSearchTool {

    private final ExternalRetrievalService externalRetrievalService;
    private final ToolCallLogService toolCallLogService;

    public WebSearchTool(ExternalRetrievalService externalRetrievalService, ToolCallLogService toolCallLogService) {
        this.externalRetrievalService = externalRetrievalService;
        this.toolCallLogService = toolCallLogService;
    }

    @Tool("Runs placeholder external search for future web evidence enrichment")
    public List<Evidence> search(String taskId, String nodeName, List<String> queries, boolean enabled) {
        Instant startedAt = Instant.now();
        Map<String, Object> input = Map.of("queries", queries, "enabled", enabled);
        try {
            List<Evidence> result = externalRetrievalService.search(taskId, queries, enabled);
            toolCallLogService.logSuccess(taskId, nodeName, "WebSearchTool", startedAt, input, result, Map.of(
                    "retrievalCount", result.size()
            ));
            return result;
        } catch (Exception exception) {
            toolCallLogService.logFailure(taskId, nodeName, "WebSearchTool", startedAt, input, exception);
            throw exception;
        }
    }

    public List<Evidence> search(String taskId, List<String> queries, boolean enabled) {
        return search(taskId, "unknown", queries, enabled);
    }
}
