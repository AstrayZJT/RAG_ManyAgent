package com.astray.insightflow.tool;

import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.service.InternalRetrievalService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KbSearchTool {

    private final InternalRetrievalService internalRetrievalService;

    public KbSearchTool(InternalRetrievalService internalRetrievalService) {
        this.internalRetrievalService = internalRetrievalService;
    }

    @Tool("Searches internal knowledge chunks for relevant evidence")
    public List<Evidence> search(String taskId, List<String> queries) {
        return internalRetrievalService.search(taskId, queries);
    }
}
