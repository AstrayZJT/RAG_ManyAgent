package com.astray.insightflow.observe.service;

import com.astray.insightflow.agent.verifier.VerifiedClaimEntity;
import com.astray.insightflow.agent.verifier.VerifiedClaimRepository;
import com.astray.insightflow.common.model.TaskProgressEvent;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.observe.api.AgentRunLogEntry;
import com.astray.insightflow.observe.api.TaskTimelineResponse;
import com.astray.insightflow.observe.api.ToolCallLogEntry;
import com.astray.insightflow.observe.domain.AgentRunLog;
import com.astray.insightflow.observe.domain.ToolCallLog;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.task.domain.ResearchTask;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.task.service.TaskService;
import org.springframework.stereotype.Service;

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
    private final JsonUtils jsonUtils;

    public TaskTimelineService(TaskService taskService,
                               TaskProgressPublisher taskProgressPublisher,
                               AgentRunLogService agentRunLogService,
                               ToolCallLogService toolCallLogService,
                               EvidenceRecordRepository evidenceRecordRepository,
                               VerifiedClaimRepository verifiedClaimRepository,
                               JsonUtils jsonUtils) {
        this.taskService = taskService;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
        this.toolCallLogService = toolCallLogService;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.verifiedClaimRepository = verifiedClaimRepository;
        this.jsonUtils = jsonUtils;
    }

    public TaskTimelineResponse getTimeline(String taskId) {
        ResearchTask task = taskService.getTask(taskId);
        List<TaskProgressEvent> progressEvents = taskProgressPublisher.history(taskId);
        List<AgentRunLog> agentLogs = agentRunLogService.getTaskLogs(taskId);
        List<ToolCallLog> toolLogs = toolCallLogService.getTaskLogs(taskId);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("tokenUsage", aggregateTokenUsage(agentLogs, toolLogs));
        metrics.put("latencyMs", agentLogs.stream().mapToLong(AgentRunLog::getLatencyMs).sum());
        metrics.put("retrievalCount", evidenceRecordRepository.findByTaskIdOrderByScoreDescCreatedAtAsc(taskId).size());
        metrics.put("citationCoverage", computeCitationCoverage(taskId));

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

        return new TaskTimelineResponse(
                taskId,
                task.getStatus().name(),
                metrics,
                progressEvents,
                agentRunEntries,
                toolCallEntries
        );
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
}
