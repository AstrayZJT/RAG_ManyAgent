package com.astray.insightflow.graph;

import com.astray.insightflow.graph.state.ResearchState;
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
    private final CompiledGraph<ResearchState> compiledGraph;

    public TaskGraphExecutor(TaskService taskService,
                             TaskProgressPublisher taskProgressPublisher,
                             ResearchGraphBuilder researchGraphBuilder) {
        this.taskService = taskService;
        this.taskProgressPublisher = taskProgressPublisher;
        this.compiledGraph = researchGraphBuilder.build();
    }

    @Async("taskExecutionExecutor")
    public CompletableFuture<Void> executeAsync(String taskId) {
        execute(taskId);
        return CompletableFuture.completedFuture(null);
    }

    public void execute(String taskId) {
        ResearchTask task = taskService.markRunning(taskId);
        taskProgressPublisher.publish(taskId, "task", "RUNNING", "Research task execution started", Map.of(
                "taskId", taskId
        ));

        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(taskId)
                    .build();

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put(ResearchState.TASK_ID, taskId);
            inputs.put(ResearchState.USER_QUERY, task.getQueryText());
            inputs.put(ResearchState.LANGUAGE, task.getLanguage());
            inputs.put(ResearchState.STATUS, "RUNNING");

            compiledGraph.invoke(inputs, config)
                    .orElseThrow(() -> new IllegalStateException("Graph execution returned no state"));

            taskService.markCompleted(taskId);
            taskProgressPublisher.publish(taskId, "task", "COMPLETED", "Research task execution completed", Map.of(
                    "taskId", taskId
            ));
        } catch (Exception exception) {
            taskService.markFailed(taskId, exception);
            taskProgressPublisher.publish(taskId, "task", "FAILED", exception.getMessage(), Map.of(
                    "taskId", taskId
            ));
            throw new IllegalStateException("Task execution failed for " + taskId, exception);
        }
    }
}
