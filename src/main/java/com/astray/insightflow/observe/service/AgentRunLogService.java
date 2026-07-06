package com.astray.insightflow.observe.service;

import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.observe.domain.AgentRunLog;
import com.astray.insightflow.observe.persistence.AgentRunLogRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AgentRunLogService {

    private final AgentRunLogRepository agentRunLogRepository;
    private final JsonUtils jsonUtils;

    public AgentRunLogService(AgentRunLogRepository agentRunLogRepository, JsonUtils jsonUtils) {
        this.agentRunLogRepository = agentRunLogRepository;
        this.jsonUtils = jsonUtils;
    }

    public void logSuccess(String taskId, String nodeName, Instant startedAt, Object output, String message) {
        save(taskId, nodeName, "SUCCESS", startedAt, output, message);
    }

    public void logFailure(String taskId, String nodeName, Instant startedAt, Exception exception) {
        save(taskId, nodeName, "FAILED", startedAt, null, exception.getMessage());
    }

    public List<AgentRunLog> getTaskLogs(String taskId) {
        return agentRunLogRepository.findByTaskIdOrderByStartedAtAsc(taskId);
    }

    private void save(String taskId, String nodeName, String status, Instant startedAt, Object output, String message) {
        AgentRunLog log = new AgentRunLog();
        log.setId(UUID.randomUUID().toString());
        log.setTaskId(taskId);
        log.setNodeName(nodeName);
        log.setStatus(status);
        log.setMessage(message);
        log.setOutputJson(output == null ? null : jsonUtils.toJson(output));
        log.setStartedAt(startedAt);
        log.setEndedAt(Instant.now());
        log.setLatencyMs(Duration.between(startedAt, log.getEndedAt()).toMillis());
        agentRunLogRepository.save(log);
    }
}
