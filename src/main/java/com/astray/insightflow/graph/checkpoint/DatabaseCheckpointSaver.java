package com.astray.insightflow.graph.checkpoint;

import com.astray.insightflow.graph.state.ResearchStateFactory;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.AbstractCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseCheckpointSaver extends AbstractCheckpointSaver {

    public static final String METADATA_EXECUTION_MODE = "insightflow.executionMode";
    public static final String METADATA_REQUESTED_NODE = "insightflow.requestedNode";

    private final GraphCheckpointMetaRepository checkpointRepository;
    private final ResearchStateFactory researchStateFactory;
    private final TaskProgressPublisher taskProgressPublisher;

    public DatabaseCheckpointSaver(GraphCheckpointMetaRepository checkpointRepository,
                                   ResearchStateFactory researchStateFactory,
                                   TaskProgressPublisher taskProgressPublisher) {
        this.checkpointRepository = checkpointRepository;
        this.researchStateFactory = researchStateFactory;
        this.taskProgressPublisher = taskProgressPublisher;
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) {
        return checkpointRepository.findByTaskIdOrderByUpdatedAtDesc(threadId(config)).stream()
                .map(this::toCheckpoint)
                .collect(java.util.stream.Collectors.toCollection(LinkedList::new));
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config,
                                      LinkedList<Checkpoint> checkpoints,
                                      Checkpoint checkpoint) {
        saveCheckpoint(config, checkpoint, "AUTO_CREATED", true);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config,
                                     LinkedList<Checkpoint> checkpoints,
                                     Checkpoint checkpoint) {
        saveCheckpoint(config, checkpoint, "UPDATED", false);
    }

    @Override
    protected BaseCheckpointSaver.Tag releaseCheckpoints(RunnableConfig config,
                                                         LinkedList<Checkpoint> checkpoints) {
        return new BaseCheckpointSaver.Tag(threadId(config), List.copyOf(checkpoints));
    }

    private Checkpoint toCheckpoint(GraphCheckpointMeta meta) {
        return Checkpoint.builder()
                .id(meta.getCheckpointId())
                .nodeId(meta.getNodeName())
                .nextNodeId(meta.getNextNodeName())
                .state(researchStateFactory.fromStateJson(meta.getStateJson()))
                .build();
    }

    private void saveCheckpoint(RunnableConfig config, Checkpoint checkpoint, String fallbackMode, boolean created) {
        GraphCheckpointMeta meta = checkpointRepository.findById(checkpoint.getId()).orElseGet(GraphCheckpointMeta::new);
        if (meta.getCheckpointId() == null) {
            meta.setCheckpointId(checkpoint.getId());
            meta.setCreatedAt(Instant.now());
        }
        meta.setTaskId(threadId(config));
        meta.setNodeName(checkpoint.getNodeId());
        meta.setNextNodeName(checkpoint.getNextNodeId());
        meta.setSaveMode(resolveMode(config, fallbackMode));
        meta.setStateJson(researchStateFactory.toStateJson(checkpoint.getState()));
        meta.setStateSummaryJson(researchStateFactory.toStateJson(researchStateFactory.summarize(checkpoint.getState())));
        meta.setUpdatedAt(Instant.now());
        checkpointRepository.save(meta);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkpointId", meta.getCheckpointId());
        payload.put("nodeName", meta.getNodeName());
        payload.put("nextNodeName", meta.getNextNodeName());
        payload.put("saveMode", meta.getSaveMode());
        taskProgressPublisher.publish(
                meta.getTaskId(),
                "checkpoint",
                created ? "SAVED" : "UPDATED",
                "Checkpoint persisted",
                payload
        );
    }

    private String resolveMode(RunnableConfig config, String fallbackMode) {
        return config.metadata(METADATA_EXECUTION_MODE)
                .map(Object::toString)
                .orElse(fallbackMode);
    }
}
