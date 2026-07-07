package com.astray.insightflow.observe.service;

import com.astray.insightflow.agent.verifier.VerifiedClaimEntity;
import com.astray.insightflow.agent.verifier.VerifiedClaimRepository;
import com.astray.insightflow.common.model.TaskProgressEvent;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.eval.api.EvaluationResponse;
import com.astray.insightflow.eval.service.EvaluationService;
import com.astray.insightflow.graph.ResearchGraphBuilder;
import com.astray.insightflow.observe.api.AgentRunLogEntry;
import com.astray.insightflow.observe.api.CheckpointLogEntry;
import com.astray.insightflow.observe.api.TaskTimelineResponse;
import com.astray.insightflow.observe.api.ToolCallLogEntry;
import com.astray.insightflow.observe.domain.AgentRunLog;
import com.astray.insightflow.observe.domain.ToolCallLog;
import com.astray.insightflow.graph.checkpoint.CheckpointService;
import com.astray.insightflow.graph.checkpoint.GraphCheckpointMeta;
import com.astray.insightflow.graph.state.ResearchStateFactory;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.task.domain.ResearchTask;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.task.service.TaskService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskTimelineService {

    private final TaskService taskService;
    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;
    private final ToolCallLogService toolCallLogService;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final VerifiedClaimRepository verifiedClaimRepository;
    private final CheckpointService checkpointService;
    private final ResearchStateFactory researchStateFactory;
    private final EvaluationService evaluationService;
    private final JsonUtils jsonUtils;

    public TaskTimelineService(TaskService taskService,
                               TaskProgressPublisher taskProgressPublisher,
                               AgentRunLogService agentRunLogService,
                               ToolCallLogService toolCallLogService,
                               EvidenceRecordRepository evidenceRecordRepository,
                               VerifiedClaimRepository verifiedClaimRepository,
                               CheckpointService checkpointService,
                               ResearchStateFactory researchStateFactory,
                               EvaluationService evaluationService,
                               JsonUtils jsonUtils) {
        this.taskService = taskService;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
        this.toolCallLogService = toolCallLogService;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.verifiedClaimRepository = verifiedClaimRepository;
        this.checkpointService = checkpointService;
        this.researchStateFactory = researchStateFactory;
        this.evaluationService = evaluationService;
        this.jsonUtils = jsonUtils;
    }

    public TaskTimelineResponse getTimeline(String taskId, String beforeNode) {
        ResearchTask task = taskService.getTask(taskId);
        List<TaskProgressEvent> progressEvents = taskProgressPublisher.history(taskId);
        List<AgentRunLog> agentLogs = agentRunLogService.getTaskLogs(taskId);
        List<ToolCallLog> toolLogs = toolCallLogService.getTaskLogs(taskId);
        List<GraphCheckpointMeta> checkpointMetas = checkpointService.list(taskId);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("tokenUsage", aggregateTokenUsage(agentLogs, toolLogs));
        metrics.put("latencyMs", agentLogs.stream().mapToLong(AgentRunLog::getLatencyMs).sum());
        metrics.put("retrievalCount", evidenceRecordRepository.findByTaskIdOrderByScoreDescCreatedAtAsc(taskId).size());
        metrics.put("citationCoverage", computeCitationCoverage(taskId));
        metrics.put("checkpointCount", checkpointMetas.size());

        List<AgentRunLogEntry> agentRunEntries = agentLogs.stream()
                .map(log -> new AgentRunLogEntry(
                        log.getNodeName(),
                        log.getStatus(),
                        log.getMessage(),
                        log.getStartedAt(),
                        log.getEndedAt(),
                        log.getLatencyMs(),
                        parseMetrics(log.getMetricsJson())
                ))
                .toList();

        List<ToolCallLogEntry> toolCallEntries = toolLogs.stream()
                .map(log -> new ToolCallLogEntry(
                        log.getNodeName(),
                        log.getToolName(),
                        log.getStatus(),
                        log.getStartedAt(),
                        log.getEndedAt(),
                        log.getLatencyMs(),
                        parseMetrics(log.getMetricsJson())
                ))
                .toList();

        List<CheckpointLogEntry> checkpointEntries = new ArrayList<>(checkpointMetas.stream()
                .map(meta -> toCheckpointEntry(meta, false))
                .toList());
        Collections.reverse(checkpointEntries);

        CheckpointLogEntry selectedCheckpoint = selectCheckpoint(taskId, beforeNode);
        EvaluationResponse latestEvaluation = evaluationService.latest(taskId).orElse(null);

        return new TaskTimelineResponse(
                taskId,
                task.getStatus().name(),
                metrics,
                progressEvents,
                agentRunEntries,
                toolCallEntries,
                checkpointEntries,
                selectedCheckpoint,
                latestEvaluation
        );
    }

    public List<CheckpointLogEntry> getCheckpointEntries(String taskId) {
        return checkpointService.list(taskId).stream()
                .map(meta -> toCheckpointEntry(meta, false))
                .toList();
    }

    public CheckpointLogEntry getCheckpoint(String taskId, String checkpointId) {
        GraphCheckpointMeta meta = checkpointService.get(taskId, checkpointId);
        return toCheckpointEntry(meta, true);
    }

    private int aggregateTokenUsage(List<AgentRunLog> agentLogs, List<ToolCallLog> toolLogs) {
        int sum = 0;
        for (AgentRunLog log : agentLogs) {
            sum += asInt(parseMetrics(log.getMetricsJson()).get("tokenUsage"));
        }
        for (ToolCallLog log : toolLogs) {
            sum += asInt(parseMetrics(log.getMetricsJson()).get("tokenUsage"));
        }
        return sum;
    }

    private double computeCitationCoverage(String taskId) {
        List<VerifiedClaimEntity> claims = verifiedClaimRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
        if (claims.isEmpty()) {
            return 0D;
        }
        long cited = claims.stream()
                .filter(claim -> claim.getSupportEvidenceJson() != null && !claim.getSupportEvidenceJson().equals("[]"))
                .count();
        return cited / (double) claims.size();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetrics(String metricsJson) {
        if (metricsJson == null || metricsJson.isBlank()) {
            return Map.of();
        }
        return jsonUtils.fromJson(metricsJson, Map.class);
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private CheckpointLogEntry selectCheckpoint(String taskId, String beforeNode) {
        if (beforeNode == null || beforeNode.isBlank()) {
            return null;
        }
        if (ResearchGraphBuilder.PLANNER.equals(beforeNode)) {
            return toCheckpointEntry(
                    checkpointService.initialSnapshot(taskId, researchStateFactory.initialState(taskService.getTask(taskId))),
                    true
            );
        }
        return checkpointService.snapshotBeforeNode(taskId, beforeNode)
                .map(meta -> toCheckpointEntry(meta, true))
                .orElse(null);
    }

    private CheckpointLogEntry toCheckpointEntry(GraphCheckpointMeta meta, boolean includeState) {
        return new CheckpointLogEntry(
                meta.getCheckpointId(),
                meta.getNodeName(),
                meta.getNextNodeName(),
                meta.getSaveMode(),
                meta.getCreatedAt(),
                meta.getUpdatedAt(),
                checkpointService.parseSummary(meta),
                includeState ? checkpointService.parseState(meta) : null
        );
    }
}
