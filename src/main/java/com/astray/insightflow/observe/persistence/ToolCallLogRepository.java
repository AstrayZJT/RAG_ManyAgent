package com.astray.insightflow.observe.persistence;

import com.astray.insightflow.observe.domain.ToolCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolCallLogRepository extends JpaRepository<ToolCallLog, String> {

    List<ToolCallLog> findByTaskIdOrderByStartedAtAsc(String taskId);
}
