package com.astray.insightflow.agent.extractor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtractedFactRepository extends JpaRepository<ExtractedFactEntity, String> {

    List<ExtractedFactEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);

    void deleteByTaskId(String taskId);
}
