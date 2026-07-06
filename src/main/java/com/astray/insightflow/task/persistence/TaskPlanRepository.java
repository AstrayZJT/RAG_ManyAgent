package com.astray.insightflow.task.persistence;

import com.astray.insightflow.task.domain.TaskPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskPlanRepository extends JpaRepository<TaskPlanEntity, String> {

    Optional<TaskPlanEntity> findByTaskId(String taskId);
}
