package com.astray.insightflow.graph.node;

import com.astray.insightflow.common.util.MetricsUtils;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.tool.KbSearchTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RetrieveInternalNode {

    private final KbSearchTool kbSearchTool;
    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;

    public RetrieveInternalNode(KbSearchTool kbSearchTool,
                                TaskProgressPublisher taskProgressPublisher,
                                AgentRunLogService agentRunLogService) {
        this.kbSearchTool = kbSearchTool;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
    }

    public Map<String, Object> execute(ResearchState state) {
        Instant startedAt = Instant.now();
        String taskId = state.taskId();
        taskProgressPublisher.publish(taskId, "retrieveInternal", "RUNNING", "Internal retrieval started", Map.of());
        try {
            List<Evidence> evidences = kbSearchTool.search(taskId, "retrieveInternal", state.subQueries());
            Map<String, Object> metrics = Map.of(
                    "tokenUsage", MetricsUtils.estimateTokens(String.join(" ", state.subQueries())),
                    "retrievalCount", evidences.size()
            );

            Map<String, Object> output = new LinkedHashMap<>();
            output.put(ResearchState.INTERNAL_EVIDENCES, evidences);
            output.put(ResearchState.STATUS, "INTERNAL_RETRIEVED");
            output.put(ResearchState.METRICS, metrics);
            output.put(ResearchState.TIMELINE, List.of("retrieveInternal completed"));
            agentRunLogService.logSuccess(taskId, "retrieveInternal", startedAt, evidences, "Internal evidence retrieved", metrics);
            taskProgressPublisher.publish(taskId, "retrieveInternal", "COMPLETED", "Internal retrieval completed", Map.of(
                    "evidenceCount", evidences.size()
            ));
            return output;
        } catch (Exception exception) {
            agentRunLogService.logFailure(taskId, "retrieveInternal", startedAt, exception);
            taskProgressPublisher.publish(taskId, "retrieveInternal", "FAILED", exception.getMessage(), Map.of());
            throw exception;
        }
    }
}
