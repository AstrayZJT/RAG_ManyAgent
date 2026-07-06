package com.astray.insightflow.tool;

import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.service.ExternalRetrievalService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSearchTool {

    private final ExternalRetrievalService externalRetrievalService;

    public WebSearchTool(ExternalRetrievalService externalRetrievalService) {
        this.externalRetrievalService = externalRetrievalService;
    }

    @Tool("Runs placeholder external search for future web evidence enrichment")
    public List<Evidence> search(String taskId, List<String> queries, boolean enabled) {
        return externalRetrievalService.search(taskId, queries, enabled);
    }
}
