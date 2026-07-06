package com.astray.insightflow.graph;

import com.astray.insightflow.graph.checkpoint.CheckpointService;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.graph.state.ResearchStateFactory;
import com.astray.insightflow.task.domain.ResearchTask;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.task.service.TaskService;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class TaskGraphExecutor {

    private final TaskService taskService;
    private final TaskProgressPublisher taskProgressPublisher;
    private final ResearchStateFactory researchStateFactory;
    private final CheckpointService checkpointService;
    private final CompiledGraph<ResearchState> compiledGraph;

    public TaskGraphExecutor(TaskService taskService,
                             TaskProgressPublisher taskProgressPublisher,
                             ResearchStateFactory researchStateFactory,
                             CheckpointService checkpointService,
                             CompiledGraph<ResearchState> compiledGraph) {
        this.taskService = taskService;
        this.taskProgressPublisher = taskProgressPublisher;
        this.researchStateFactory = researchStateFactory;
        this.checkpointService = checkpointService;
        this.compiledGraph = compiledGraph;
    }

    @Async("taskExecutionExecutor")
    public CompletableFuture<Void> executeAsync(String taskId) {
        execute(taskId);
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutionExecutor")
    public CompletableFuture<Void> resumeAsync(String taskId, String checkpointId, Map<String, Object> statePatch) {
        resume(taskId, checkpointId, statePatch);
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutionExecutor")
    public CompletableFuture<Void> rerunAsync(String taskId, String nodeName, Map<String, Object> statePatch) {
        rerun(taskId, nodeName, statePatch);
        return CompletableFuture.completedFuture(null);
    }

    public void execute(String taskId) {
        ResearchTask task = taskService.markRunning(taskId);
        publishTaskState(taskId, "RUNNING", "Research task execution started", Map.of(
                "taskId", taskId,
                "mode", "run"
        ));

        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(taskId)
                    .build();

            compiledGraph.invoke(researchStateFactory.initialState(task), config)
                    .orElseThrow(() -> new IllegalStateException("Graph execution returned no state"));

            complete(taskId, "Research task execution completed", Map.of(
                    "taskId", taskId,
                    "mode", "run"
            ));
        } catch (Exception exception) {
            fail(taskId, exception, Map.of(
                    "taskId", taskId,
                    "mode", "run"
            ));
            throw new IllegalStateException("Task execution failed for " + taskId, exception);
        }
    }

    public void resume(String taskId, String checkpointId, Map<String, Object> statePatch) {
        taskService.getTask(taskId);
        taskService.markRunning(taskId);
        publishTaskState(taskId, "RUNNING", "Checkpoint resume started", resumePayload("resume", checkpointId, null));

        try {
            CheckpointService.PreparedExecution preparedExecution = checkpointService.prepareResume(taskId, checkpointId, statePatch);
            compiledGraph.invoke((Map<String, Object>) null, preparedExecution.runnableConfig())
                    .orElseThrow(() -> new IllegalStateException("Graph resume returned no state"));

            complete(taskId, "Checkpoint resume completed", resumePayload("resume", preparedExecution.checkpointId(), preparedExecution.nextNode()));
        } catch (Exception exception) {
            fail(taskId, exception, resumePayload("resume", checkpointId, null));
            throw new IllegalStateException("Task resume failed for " + taskId, exception);
        }
    }

    public void rerun(String taskId, String nodeName, Map<String, Object> statePatch) {
        taskService.getTask(taskId);
        taskService.markRunning(taskId);
        publishTaskState(taskId, "RUNNING", "Node rerun started", rerunPayload(nodeName, null, null));

        try {
            CheckpointService.PreparedExecution preparedExecution = checkpointService.prepareRerun(taskId, nodeName, statePatch);
            compiledGraph.invoke((Map<String, Object>) null, preparedExecution.runnableConfig())
                    .orElseThrow(() -> new IllegalStateException("Graph rerun returned no state"));

            complete(taskId, "Node rerun completed", rerunPayload(nodeName, preparedExecution.checkpointId(), preparedExecution.nextNode()));
        } catch (Exception exception) {
            fail(taskId, exception, rerunPayload(nodeName, null, null));
            throw new IllegalStateException("Task rerun failed for " + taskId + " from node " + nodeName, exception);
        }
    }

    private void complete(String taskId, String message, Map<String, Object> payload) {
        taskService.markCompleted(taskId);
        publishTaskState(taskId, "COMPLETED", message, payload);
    }

    private void fail(String taskId, Exception exception, Map<String, Object> payload) {
        taskService.markFailed(taskId, exception);
        publishTaskState(taskId, "FAILED", exception.getMessage(), payload);
    }

    private void publishTaskState(String taskId, String status, String message, Map<String, Object> payload) {
        taskProgressPublisher.publish(taskId, "task", status, message, payload);
    }

    private Map<String, Object> resumePayload(String mode, String checkpointId, String nextNode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", mode);
        if (checkpointId != null) {
            payload.put("checkpointId", checkpointId);
        }
        if (nextNode != null) {
            payload.put("nextNode", nextNode);
        }
        return payload;
    }

    private Map<String, Object> rerunPayload(String requestedNode, String checkpointId, String actualNode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", "rerun");
        payload.put("requestedNode", requestedNode);
        if (checkpointId != null) {
            payload.put("checkpointId", checkpointId);
        }
        if (actualNode != null) {
            payload.put("nextNode", actualNode);
        }
        return payload;
    }
}
