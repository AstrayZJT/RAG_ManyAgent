package com.astray.insightflow.graph.node;

import com.astray.insightflow.common.util.MetricsUtils;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.tool.WebSearchTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RetrieveExternalNode {

    private final WebSearchTool webSearchTool;
    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;

    public RetrieveExternalNode(WebSearchTool webSearchTool,
                                TaskProgressPublisher taskProgressPublisher,
                                AgentRunLogService agentRunLogService) {
        this.webSearchTool = webSearchTool;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
    }

    public Map<String, Object> execute(ResearchState state) {
        Instant startedAt = Instant.now();
        String taskId = state.taskId();
        taskProgressPublisher.publish(taskId, "retrieveExternal", "RUNNING", "External retrieval started", Map.of(
                "enabled", state.needExternalSearch()
        ));
        try {
            List<Evidence> evidences = webSearchTool.search(taskId, "retrieveExternal", state.subQueries(), state.needExternalSearch());
            Map<String, Object> metrics = Map.of(
                    "tokenUsage", MetricsUtils.estimateTokens(String.join(" ", state.subQueries())),
                    "retrievalCount", evidences.size()
            );

            Map<String, Object> output = new LinkedHashMap<>();
            output.put(ResearchState.EXTERNAL_EVIDENCES, evidences);
            output.put(ResearchState.STATUS, "EXTERNAL_RETRIEVED");
            output.put(ResearchState.METRICS, metrics);
            output.put(ResearchState.TIMELINE, List.of("retrieveExternal completed"));
            agentRunLogService.logSuccess(taskId, "retrieveExternal", startedAt, evidences, "External evidence retrieved", metrics);
            taskProgressPublisher.publish(taskId, "retrieveExternal", "COMPLETED", "External retrieval completed", Map.of(
                    "evidenceCount", evidences.size()
            ));
            return output;
        } catch (Exception exception) {
            agentRunLogService.logFailure(taskId, "retrieveExternal", startedAt, exception);
            taskProgressPublisher.publish(taskId, "retrieveExternal", "FAILED", exception.getMessage(), Map.of());
            throw exception;
        }
    }
}
