package com.astray.insightflow.graph.node;

import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.tool.RerankTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MergeRerankNode {

    private final RerankTool rerankTool;
    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;

    public MergeRerankNode(RerankTool rerankTool,
                           TaskProgressPublisher taskProgressPublisher,
                           AgentRunLogService agentRunLogService) {
        this.rerankTool = rerankTool;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
    }

    public Map<String, Object> execute(ResearchState state) {
        Instant startedAt = Instant.now();
        String taskId = state.taskId();
        taskProgressPublisher.publish(taskId, "mergeRerank", "RUNNING", "Merging and reranking evidences", Map.of());
        try {
            List<Evidence> merged = new ArrayList<>();
            merged.addAll(state.internalEvidences());
            merged.addAll(state.externalEvidences());
            List<Evidence> reranked = rerankTool.rerank(taskId, "mergeRerank", merged);
            Map<String, Object> metrics = Map.of("retrievalCount", reranked.size());

            Map<String, Object> output = new LinkedHashMap<>();
            output.put(ResearchState.MERGED_EVIDENCES, reranked);
            output.put(ResearchState.STATUS, "EVIDENCE_READY");
            output.put(ResearchState.METRICS, metrics);
            output.put(ResearchState.TIMELINE, List.of("mergeRerank completed"));
            agentRunLogService.logSuccess(taskId, "mergeRerank", startedAt, reranked, "Merged and reranked evidence", metrics);
            taskProgressPublisher.publish(taskId, "mergeRerank", "COMPLETED", "Evidence merged", Map.of(
                    "evidenceCount", reranked.size()
            ));
            return output;
        } catch (Exception exception) {
            agentRunLogService.logFailure(taskId, "mergeRerank", startedAt, exception);
            taskProgressPublisher.publish(taskId, "mergeRerank", "FAILED", exception.getMessage(), Map.of());
            throw exception;
        }
    }
}
