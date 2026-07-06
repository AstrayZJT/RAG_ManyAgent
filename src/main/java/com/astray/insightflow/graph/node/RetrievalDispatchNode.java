package com.astray.insightflow.graph.node;

import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RetrievalDispatchNode {

    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;

    public RetrievalDispatchNode(TaskProgressPublisher taskProgressPublisher,
                                 AgentRunLogService agentRunLogService) {
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
    }

    public Map<String, Object> execute(ResearchState state) {
        Instant startedAt = Instant.now();
        String taskId = state.taskId();
        taskProgressPublisher.publish(taskId, "retrievalStart", "RUNNING", "Retrieval dispatch started", Map.of(
                "needExternalSearch", state.needExternalSearch()
        ));
        try {
            int branchCount = state.needExternalSearch() ? 2 : 1;
            Map<String, Object> metrics = Map.of("parallelBranches", branchCount);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put(ResearchState.STATUS, "RETRIEVAL_DISPATCHED");
            output.put(ResearchState.METRICS, metrics);
            output.put(ResearchState.TIMELINE, List.of("retrievalStart completed"));
            agentRunLogService.logSuccess(taskId, "retrievalStart", startedAt, metrics, "Retrieval branches dispatched", metrics);
            taskProgressPublisher.publish(taskId, "retrievalStart", "COMPLETED", "Retrieval dispatch completed", Map.of(
                    "parallelBranches", branchCount
            ));
            return output;
        } catch (Exception exception) {
            agentRunLogService.logFailure(taskId, "retrievalStart", startedAt, exception);
            taskProgressPublisher.publish(taskId, "retrievalStart", "FAILED", exception.getMessage(), Map.of());
            throw exception;
        }
    }
}
