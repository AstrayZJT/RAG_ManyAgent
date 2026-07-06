package com.astray.insightflow.graph.node;

import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.service.ExternalRetrievalService;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RetrieveExternalNode {

    private final ExternalRetrievalService externalRetrievalService;
    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;

    public RetrieveExternalNode(ExternalRetrievalService externalRetrievalService,
                                TaskProgressPublisher taskProgressPublisher,
                                AgentRunLogService agentRunLogService) {
        this.externalRetrievalService = externalRetrievalService;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
    }

    public Map<String, Object> execute(ResearchState state) {
        Instant startedAt = Instant.now();
        String taskId = state.taskId();
        taskProgressPublisher.publish(taskId, "retrieveExternal", "RUNNING", "External retrieval placeholder started", Map.of(
                "enabled", state.needExternalSearch()
        ));
        try {
            List<Evidence> evidences = externalRetrievalService.search(taskId, state.subQueries(), state.needExternalSearch());
            Map<String, Object> output = new LinkedHashMap<>();
            output.put(ResearchState.EXTERNAL_EVIDENCES, evidences);
            output.put(ResearchState.STATUS, "EXTERNAL_RETRIEVED");
            output.put(ResearchState.METRICS, Map.of("externalRetrievalCount", evidences.size()));
            output.put(ResearchState.TIMELINE, List.of("retrieveExternal completed"));
            agentRunLogService.logSuccess(taskId, "retrieveExternal", startedAt, evidences, "External retrieval placeholder completed");
            taskProgressPublisher.publish(taskId, "retrieveExternal", "COMPLETED", "External retrieval placeholder completed", Map.of(
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
