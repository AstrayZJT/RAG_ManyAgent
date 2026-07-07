package com.astray.insightflow.tool;

import com.astray.insightflow.config.AgentProperties;
import com.astray.insightflow.observe.service.ToolCallLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RerankTool {

    private final ToolCallLogService toolCallLogService;
    private final int maxMergedResults;

    public RerankTool(ToolCallLogService toolCallLogService, AgentProperties agentProperties) {
        this.toolCallLogService = toolCallLogService;
        this.maxMergedResults = Math.max(8, agentProperties.search().maxResults() + agentProperties.search().maxExternalPages());
    }

    @Tool("Deduplicates and sorts evidence by descending relevance score")
    public List<Evidence> rerank(String taskId, String nodeName, List<Evidence> evidences) {
        Instant startedAt = Instant.now();
        try {
            Map<String, Evidence> deduplicated = new LinkedHashMap<>();
            for (Evidence evidence : evidences) {
                String key = uniqueKey(evidence);
                Evidence existing = deduplicated.get(key);
                if (existing == null || evidence.getScore() > existing.getScore()) {
                    deduplicated.put(key, evidence);
                }
            }
            List<Evidence> sorted = new ArrayList<>(deduplicated.values());
            sorted.sort(Comparator
                    .comparingDouble(Evidence::getScore).reversed()
                    .thenComparing(evidence -> evidence.getSourceType() == null ? "" : evidence.getSourceType().name()));
            if (sorted.size() > maxMergedResults) {
                sorted = new ArrayList<>(sorted.subList(0, maxMergedResults));
            }
            toolCallLogService.logSuccess(taskId, nodeName, "RerankTool", startedAt,
                    Map.of("inputCount", evidences.size()), sorted, Map.of(
                            "retrievalCount", sorted.size(),
                            "deduplicatedCount", deduplicated.size()
                    ));
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

    private String uniqueKey(Evidence evidence) {
        if (StringUtils.hasText(evidence.getUrl())) {
            return "url:" + evidence.getUrl().trim().toLowerCase();
        }
        String title = evidence.getTitle() == null ? "" : evidence.getTitle().trim().toLowerCase();
        String snippet = evidence.getSnippet() == null ? "" : evidence.getSnippet().trim().toLowerCase();
        return "text:" + title + "|" + snippet;
    }
}
