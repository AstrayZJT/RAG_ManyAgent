package com.astray.insightflow.task.api;

import com.astray.insightflow.agent.planner.PlanResult;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.eval.api.EvaluationResponse;
import com.astray.insightflow.eval.service.EvaluationService;
import com.astray.insightflow.graph.TaskGraphExecutor;
import com.astray.insightflow.observe.api.TaskTimelineResponse;
import com.astray.insightflow.observe.service.TaskTimelineService;
import com.astray.insightflow.report.api.ReportCitationResponse;
import com.astray.insightflow.report.api.ReportResponse;
import com.astray.insightflow.report.service.ReportService;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.task.domain.ResearchTask;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskGraphExecutor taskGraphExecutor;
    private final TaskProgressPublisher taskProgressPublisher;
    private final ReportService reportService;
    private final TaskTimelineService taskTimelineService;
    private final EvaluationService evaluationService;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final JsonUtils jsonUtils;

    public TaskController(TaskService taskService,
                          TaskGraphExecutor taskGraphExecutor,
                          TaskProgressPublisher taskProgressPublisher,
                          ReportService reportService,
                          TaskTimelineService taskTimelineService,
                          EvaluationService evaluationService,
                          EvidenceRecordRepository evidenceRecordRepository,
                          JsonUtils jsonUtils) {
        this.taskService = taskService;
        this.taskGraphExecutor = taskGraphExecutor;
        this.taskProgressPublisher = taskProgressPublisher;
        this.reportService = reportService;
        this.taskTimelineService = taskTimelineService;
        this.evaluationService = evaluationService;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.jsonUtils = jsonUtils;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        String language = StringUtils.hasText(request.language()) ? request.language() : "zh-CN";
        ResearchTask task = taskService.createTask(request.query().trim(), language.trim());
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> listTasks() {
        return ResponseEntity.ok(taskService.listTasks().stream().map(TaskResponse::from).toList());
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<RunTaskResponse> runTask(@PathVariable("id") String taskId) {
        taskService.getTask(taskId);
        taskGraphExecutor.executeAsync(taskId);
        return ResponseEntity.accepted().body(new RunTaskResponse(taskId, "ACCEPTED", "Task execution started"));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<RunTaskResponse> resumeTask(@PathVariable("id") String taskId,
                                                      @RequestBody(required = false) ResumeTaskRequest request) {
        taskService.getTask(taskId);
        String checkpointId = request == null ? null : request.checkpointId();
        Map<String, Object> statePatch = request == null || request.statePatch() == null ? Map.of() : request.statePatch();
        taskGraphExecutor.resumeAsync(taskId, checkpointId, statePatch);
        return ResponseEntity.accepted().body(new RunTaskResponse(taskId, "ACCEPTED", "Task resume started"));
    }

    @PostMapping("/{id}/rerun/{node}")
    public ResponseEntity<RunTaskResponse> rerunTask(@PathVariable("id") String taskId,
                                                     @PathVariable("node") String node,
                                                     @RequestBody(required = false) RerunTaskRequest request) {
        taskService.getTask(taskId);
        Map<String, Object> statePatch = request == null || request.statePatch() == null ? Map.of() : request.statePatch();
        taskGraphExecutor.rerunAsync(taskId, node, statePatch);
        return ResponseEntity.accepted().body(new RunTaskResponse(taskId, "ACCEPTED", "Task rerun started"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDetailResponse> getTask(@PathVariable("id") String taskId) {
        ResearchTask task = taskService.getTask(taskId);
        PlanResult plan = taskService.getTaskPlan(taskId)
                .map(entity -> jsonUtils.fromJson(entity.getPlanJson(), PlanResult.class))
                .orElse(null);
        boolean reportAvailable = taskService.getFinalReport(taskId).isPresent();
        TaskDetailResponse response = new TaskDetailResponse(
                task.getId(),
                task.getQueryText(),
                task.getLanguage(),
                task.getStatus().name(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getCompletedAt(),
                taskService.getEvidenceCount(taskId),
                reportAvailable,
                plan
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<ReportResponse> getReport(@PathVariable("id") String taskId) {
        var report = reportService.getByTaskId(taskId);
        var draft = jsonUtils.fromJson(report.getReportJson(), com.astray.insightflow.agent.writer.ReportDraft.class);
        return ResponseEntity.ok(new ReportResponse(
                taskId,
                report.getTitle(),
                report.getReportMarkdown(),
                draft,
                report.getUpdatedAt(),
                evidenceRecordRepository.findByTaskIdOrderByScoreDescCreatedAtAsc(taskId).stream()
                        .map(ReportCitationResponse::from)
                        .toList()
        ));
    }

    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("id") String taskId) {
        taskService.getTask(taskId);
        return taskProgressPublisher.subscribe(taskId);
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<TaskTimelineResponse> getTimeline(@PathVariable("id") String taskId,
                                                            @RequestParam(value = "beforeNode", required = false) String beforeNode) {
        return ResponseEntity.ok(taskTimelineService.getTimeline(taskId, beforeNode));
    }

    @GetMapping("/{id}/checkpoints")
    public ResponseEntity<List<com.astray.insightflow.observe.api.CheckpointLogEntry>> listCheckpoints(@PathVariable("id") String taskId) {
        return ResponseEntity.ok(taskTimelineService.getCheckpointEntries(taskId));
    }

    @GetMapping("/{id}/checkpoints/{checkpointId}")
    public ResponseEntity<com.astray.insightflow.observe.api.CheckpointLogEntry> getCheckpoint(@PathVariable("id") String taskId,
                                                                                              @PathVariable("checkpointId") String checkpointId) {
        return ResponseEntity.ok(taskTimelineService.getCheckpoint(taskId, checkpointId));
    }

    @PostMapping("/{id}/evaluate")
    public ResponseEntity<EvaluationResponse> evaluate(@PathVariable("id") String taskId) {
        return ResponseEntity.ok(evaluationService.evaluate(taskId));
    }
}
