package com.astray.insightflow.observe.service;

import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.observe.domain.ToolCallLog;
import com.astray.insightflow.observe.persistence.ToolCallLogRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ToolCallLogService {

    private final ToolCallLogRepository toolCallLogRepository;
    private final JsonUtils jsonUtils;

    public ToolCallLogService(ToolCallLogRepository toolCallLogRepository, JsonUtils jsonUtils) {
        this.toolCallLogRepository = toolCallLogRepository;
        this.jsonUtils = jsonUtils;
    }

    public void logSuccess(String taskId, String nodeName, String toolName, Instant startedAt,
                           Object input, Object output, Map<String, Object> metrics) {
        save(taskId, nodeName, toolName, "SUCCESS", startedAt, input, output, metrics);
    }

    public void logFailure(String taskId, String nodeName, String toolName, Instant startedAt,
                           Object input, Exception exception) {
        save(taskId, nodeName, toolName, "FAILED", startedAt, input, Map.of("error", exception.toString()), Map.of());
    }

    public List<ToolCallLog> getTaskLogs(String taskId) {
        return toolCallLogRepository.findByTaskIdOrderByStartedAtAsc(taskId);
    }

    private void save(String taskId, String nodeName, String toolName, String status, Instant startedAt,
                      Object input, Object output, Map<String, Object> metrics) {
        ToolCallLog log = new ToolCallLog();
        log.setId(UUID.randomUUID().toString());
        log.setTaskId(taskId);
        log.setNodeName(nodeName);
        log.setToolName(toolName);
        log.setStatus(status);
        log.setInputJson(input == null ? null : jsonUtils.toJson(input));
        log.setOutputJson(output == null ? null : jsonUtils.toJson(output));
        log.setMetricsJson(metrics == null || metrics.isEmpty() ? null : jsonUtils.toJson(metrics));
        log.setStartedAt(startedAt);
        log.setEndedAt(Instant.now());
        log.setLatencyMs(Duration.between(startedAt, log.getEndedAt()).toMillis());
        toolCallLogRepository.save(log);
    }
}
