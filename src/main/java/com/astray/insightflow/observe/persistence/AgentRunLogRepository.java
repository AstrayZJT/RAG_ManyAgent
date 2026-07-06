package com.astray.insightflow.observe.persistence;

import com.astray.insightflow.observe.domain.AgentRunLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRunLogRepository extends JpaRepository<AgentRunLog, String> {

    List<AgentRunLog> findByTaskIdOrderByStartedAtAsc(String taskId);
}
