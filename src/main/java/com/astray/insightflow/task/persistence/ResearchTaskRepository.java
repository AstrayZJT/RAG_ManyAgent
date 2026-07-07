package com.astray.insightflow.task.persistence;

import com.astray.insightflow.task.domain.ResearchTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResearchTaskRepository extends JpaRepository<ResearchTask, String> {

    List<ResearchTask> findAllByOrderByCreatedAtDesc();
}
