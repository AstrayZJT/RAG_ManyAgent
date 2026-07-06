package com.astray.insightflow.graph.checkpoint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GraphCheckpointMetaRepository extends JpaRepository<GraphCheckpointMeta, String> {

    List<GraphCheckpointMeta> findByTaskIdOrderByUpdatedAtDesc(String taskId);

    Optional<GraphCheckpointMeta> findFirstByTaskIdOrderByUpdatedAtDesc(String taskId);

    Optional<GraphCheckpointMeta> findByTaskIdAndCheckpointId(String taskId, String checkpointId);

    List<GraphCheckpointMeta> findByTaskIdAndNextNodeNameOrderByUpdatedAtDesc(String taskId, String nextNodeName);
}
