package com.astray.insightflow.observe.api;

import com.astray.insightflow.common.model.TaskProgressEvent;
import com.astray.insightflow.eval.api.EvaluationResponse;

import java.util.List;
import java.util.Map;

public record TaskTimelineResponse(
        String taskId,
        String status,
        Map<String, Object> metrics,
        List<TaskProgressEvent> progressEvents,
        List<AgentRunLogEntry> agentRuns,
        List<ToolCallLogEntry> toolCalls,
        List<CheckpointLogEntry> checkpoints,
        CheckpointLogEntry selectedCheckpoint,
        EvaluationResponse latestEvaluation
) {
}
