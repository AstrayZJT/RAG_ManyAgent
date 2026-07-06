package com.astray.insightflow.tool;

import com.astray.insightflow.config.AgentProperties;
import com.astray.insightflow.observe.service.ToolCallLogService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class WebFetchTool {

    private final int maxCharacters;
    private final ToolCallLogService toolCallLogService;

    public WebFetchTool(AgentProperties agentProperties, ToolCallLogService toolCallLogService) {
        this.maxCharacters = agentProperties.webpage().maxCharacters();
        this.toolCallLogService = toolCallLogService;
    }

    @Tool("Fetches placeholder webpage content by url")
    public String fetch(String taskId, String nodeName, String url) {
        Instant startedAt = Instant.now();
        Map<String, Object> input = Map.of("url", url);
        try {
            String content = ("Fetched stub content for " + url + ". This placeholder can be replaced with jsoup-based正文抓取。");
            String trimmed = content.length() > maxCharacters ? content.substring(0, maxCharacters) : content;
            toolCallLogService.logSuccess(taskId, nodeName, "WebFetchTool", startedAt, input, Map.of("content", trimmed),
                    Map.of("maxCharacters", maxCharacters));
            return trimmed;
        } catch (Exception exception) {
            toolCallLogService.logFailure(taskId, nodeName, "WebFetchTool", startedAt, input, exception);
            throw exception;
        }
    }
}
