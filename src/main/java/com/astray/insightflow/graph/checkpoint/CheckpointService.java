package com.astray.insightflow.graph.checkpoint;

import com.astray.insightflow.common.exception.NotFoundException;
import com.astray.insightflow.graph.ResearchGraphBuilder;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.graph.state.ResearchStateFactory;
import com.astray.insightflow.graph.subgraph.RetrievalSubgraphBuilder;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CheckpointService {

    private final GraphCheckpointMetaRepository checkpointRepository;
    private final ResearchStateFactory researchStateFactory;
    private final CompiledGraph<ResearchState> compiledGraph;

    public CheckpointService(GraphCheckpointMetaRepository checkpointRepository,
                             ResearchStateFactory researchStateFactory,
                             CompiledGraph<ResearchState> compiledGraph) {
        this.checkpointRepository = checkpointRepository;
        this.researchStateFactory = researchStateFactory;
        this.compiledGraph = compiledGraph;
    }

    public List<GraphCheckpointMeta> list(String taskId) {
        return checkpointRepository.findByTaskIdOrderByUpdatedAtDesc(taskId);
    }

    public Optional<GraphCheckpointMeta> latest(String taskId) {
        return checkpointRepository.findFirstByTaskIdOrderByUpdatedAtDesc(taskId);
    }

    public GraphCheckpointMeta get(String taskId, String checkpointId) {
        return checkpointRepository.findByTaskIdAndCheckpointId(taskId, checkpointId)
                .orElseThrow(() -> new NotFoundException("Checkpoint not found: " + checkpointId));
    }

    public Optional<GraphCheckpointMeta> snapshotBeforeNode(String taskId, String nodeName) {
        String normalized = normalizeRequestedNode(nodeName);
        if (ResearchGraphBuilder.PLANNER.equals(normalized)) {
            return Optional.empty();
        }
        List<GraphCheckpointMeta> matches = checkpointRepository.findByTaskIdAndNextNodeNameOrderByUpdatedAtDesc(taskId, normalized);
        if (!matches.isEmpty()) {
            return Optional.of(matches.get(0));
        }
        if (isRetrievalAlias(normalized)) {
            return checkpointRepository.findByTaskIdAndNextNodeNameOrderByUpdatedAtDesc(taskId, RetrievalSubgraphBuilder.RETRIEVAL_START)
                    .stream()
                    .findFirst();
        }
        return Optional.empty();
    }

    public Optional<ResearchState> loadLatestState(String taskId) {
        return latest(taskId).map(this::toState);
    }

    public ResearchState toState(GraphCheckpointMeta meta) {
        return researchStateFactory.apply(researchStateFactory.fromStateJson(meta.getStateJson()));
    }

    public Map<String, Object> parseSummary(GraphCheckpointMeta meta) {
        if (!StringUtils.hasText(meta.getStateSummaryJson())) {
            return researchStateFactory.summarize(researchStateFactory.fromStateJson(meta.getStateJson()));
        }
        return researchStateFactory.readJsonMap(meta.getStateSummaryJson());
    }

    public Map<String, Object> parseState(GraphCheckpointMeta meta) {
        return researchStateFactory.readJsonMap(meta.getStateJson());
    }

    public PreparedExecution prepareResume(String taskId,
                                           String checkpointId,
                                           Map<String, Object> statePatch) throws Exception {
        GraphCheckpointMeta meta = StringUtils.hasText(checkpointId)
                ? get(taskId, checkpointId)
                : latest(taskId).orElseThrow(() -> new NotFoundException("No checkpoint found for task: " + taskId));

        if (!StringUtils.hasText(meta.getNextNodeName()) || StateGraph.END.equals(meta.getNextNodeName())) {
            throw new IllegalStateException("Latest checkpoint is already at graph end");
        }

        RunnableConfig config = buildConfig(taskId, meta.getCheckpointId(), meta.getNextNodeName(), "resume", null);
        if (statePatch != null && !statePatch.isEmpty()) {
            config = compiledGraph.updateState(config, researchStateFactory.coercePatch(statePatch));
            config = RunnableConfig.builder(config)
                    .threadId(taskId)
                    .checkPointId(meta.getCheckpointId())
                    .nextNode(meta.getNextNodeName())
                    .putMetadata(DatabaseCheckpointSaver.METADATA_EXECUTION_MODE, "resume")
                    .build();
        }
        return new PreparedExecution(meta.getCheckpointId(), meta.getNextNodeName(), config);
    }

    public PreparedExecution prepareRerun(String taskId,
                                          String requestedNode,
                                          Map<String, Object> statePatch) throws Exception {
        String normalizedNode = normalizeRequestedNode(requestedNode);
        if (ResearchGraphBuilder.PLANNER.equals(normalizedNode)) {
            throw new IllegalArgumentException("Planner rerun is not supported via checkpoint; use /run for a full rerun");
        }

        String actualStartNode = isRetrievalAlias(normalizedNode)
                ? RetrievalSubgraphBuilder.RETRIEVAL_START
                : normalizedNode;

        GraphCheckpointMeta meta = snapshotBeforeNode(taskId, actualStartNode)
                .orElseThrow(() -> new NotFoundException("No checkpoint snapshot found before node: " + requestedNode));

        RunnableConfig config = buildConfig(taskId, meta.getCheckpointId(), actualStartNode, "rerun", requestedNode);
        if (statePatch != null && !statePatch.isEmpty()) {
            config = compiledGraph.updateState(config, researchStateFactory.coercePatch(statePatch));
            config = RunnableConfig.builder(config)
                    .threadId(taskId)
                    .checkPointId(meta.getCheckpointId())
                    .nextNode(actualStartNode)
                    .putMetadata(DatabaseCheckpointSaver.METADATA_EXECUTION_MODE, "rerun")
                    .putMetadata(DatabaseCheckpointSaver.METADATA_REQUESTED_NODE, requestedNode)
                    .build();
        }
        return new PreparedExecution(meta.getCheckpointId(), actualStartNode, config);
    }

    public GraphCheckpointMeta initialSnapshot(String taskId, Map<String, Object> initialState) {
        GraphCheckpointMeta meta = new GraphCheckpointMeta();
        meta.setCheckpointId("INITIAL-" + taskId);
        meta.setTaskId(taskId);
        meta.setNodeName(StateGraph.START);
        meta.setNextNodeName(ResearchGraphBuilder.PLANNER);
        meta.setSaveMode("INITIAL");
        meta.setStateJson(researchStateFactory.toStateJson(initialState));
        meta.setStateSummaryJson(researchStateFactory.toStateJson(researchStateFactory.summarize(initialState)));
        meta.setCreatedAt(Instant.EPOCH);
        meta.setUpdatedAt(Instant.EPOCH);
        return meta;
    }

    private RunnableConfig buildConfig(String taskId,
                                       String checkpointId,
                                       String nextNode,
                                       String executionMode,
                                       String requestedNode) {
        RunnableConfig.Builder builder = RunnableConfig.builder()
                .threadId(taskId)
                .checkPointId(checkpointId)
                .nextNode(nextNode)
                .putMetadata(DatabaseCheckpointSaver.METADATA_EXECUTION_MODE, executionMode);
        if (StringUtils.hasText(requestedNode)) {
            builder.putMetadata(DatabaseCheckpointSaver.METADATA_REQUESTED_NODE, requestedNode);
        }
        return builder.build();
    }

    private String normalizeRequestedNode(String requestedNode) {
        if (!StringUtils.hasText(requestedNode)) {
            throw new IllegalArgumentException("Node name must not be blank");
        }
        String trimmed = requestedNode.trim();
        return switch (trimmed) {
            case "retrieval" -> RetrievalSubgraphBuilder.RETRIEVAL_START;
            case "merge" -> RetrievalSubgraphBuilder.MERGE_RERANK;
            default -> trimmed;
        };
    }

    private boolean isRetrievalAlias(String nodeName) {
        return RetrievalSubgraphBuilder.RETRIEVAL_START.equals(nodeName)
                || RetrievalSubgraphBuilder.RETRIEVE_INTERNAL.equals(nodeName)
                || RetrievalSubgraphBuilder.RETRIEVE_EXTERNAL.equals(nodeName)
                || "retrieval".equals(nodeName);
    }

    public record PreparedExecution(String checkpointId, String nextNode, RunnableConfig runnableConfig) {
    }
}
