package com.astray.insightflow.graph.node;

import com.astray.insightflow.agent.planner.PlanResult;
import com.astray.insightflow.agent.planner.PlannerAgent;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.task.domain.TaskPlanEntity;
import com.astray.insightflow.task.persistence.TaskPlanRepository;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PlannerNode {

    private final PlannerAgent plannerAgent;
    private final TaskPlanRepository taskPlanRepository;
    private final JsonUtils jsonUtils;
    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;

    public PlannerNode(PlannerAgent plannerAgent,
                       TaskPlanRepository taskPlanRepository,
                       JsonUtils jsonUtils,
                       TaskProgressPublisher taskProgressPublisher,
                       AgentRunLogService agentRunLogService) {
        this.plannerAgent = plannerAgent;
        this.taskPlanRepository = taskPlanRepository;
        this.jsonUtils = jsonUtils;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
    }

    public Map<String, Object> execute(ResearchState state) {
        Instant startedAt = Instant.now();
        String taskId = state.taskId();
        taskProgressPublisher.publish(taskId, "planner", "RUNNING", "Planner node started", Map.of());
        try {
            PlanResult plan = plannerAgent.plan(state.userQuery(), state.language());
            TaskPlanEntity entity = taskPlanRepository.findByTaskId(taskId).orElseGet(TaskPlanEntity::new);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID().toString());
                entity.setTaskId(taskId);
                entity.setCreatedAt(Instant.now());
            }
            entity.setPlanJson(jsonUtils.toJson(plan));
            entity.setUpdatedAt(Instant.now());
            taskPlanRepository.save(entity);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put(ResearchState.PLAN, plan);
            output.put(ResearchState.SUB_QUERIES, plan.getSubQueries());
            output.put(ResearchState.NEED_EXTERNAL_SEARCH, plan.isNeedExternalSearch());
            output.put(ResearchState.STATUS, "PLANNED");
            output.put(ResearchState.METRICS, Map.of("retrievalCount", 0));
            output.put(ResearchState.TIMELINE, List.of("planner completed"));
            agentRunLogService.logSuccess(taskId, "planner", startedAt, plan, "Planner produced structured plan");
            taskProgressPublisher.publish(taskId, "planner", "COMPLETED", "Planner node completed", Map.of(
                    "subQueries", plan.getSubQueries().size(),
                    "needExternalSearch", plan.isNeedExternalSearch()
            ));
            return output;
        } catch (Exception exception) {
            agentRunLogService.logFailure(taskId, "planner", startedAt, exception);
            taskProgressPublisher.publish(taskId, "planner", "FAILED", exception.getMessage(), Map.of());
            throw exception;
        }
    }
}
