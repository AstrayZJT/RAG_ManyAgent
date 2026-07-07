package com.astray.insightflow.task.service;

import com.astray.insightflow.common.exception.NotFoundException;
import com.astray.insightflow.report.domain.FinalReport;
import com.astray.insightflow.report.persistence.FinalReportRepository;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.task.domain.ResearchTask;
import com.astray.insightflow.task.domain.ResearchTaskStatus;
import com.astray.insightflow.task.domain.TaskPlanEntity;
import com.astray.insightflow.task.persistence.ResearchTaskRepository;
import com.astray.insightflow.task.persistence.TaskPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaskService {

    private final ResearchTaskRepository researchTaskRepository;
    private final TaskPlanRepository taskPlanRepository;
    private final FinalReportRepository finalReportRepository;
    private final EvidenceRecordRepository evidenceRecordRepository;

    public TaskService(ResearchTaskRepository researchTaskRepository,
                       TaskPlanRepository taskPlanRepository,
                       FinalReportRepository finalReportRepository,
                       EvidenceRecordRepository evidenceRecordRepository) {
        this.researchTaskRepository = researchTaskRepository;
        this.taskPlanRepository = taskPlanRepository;
        this.finalReportRepository = finalReportRepository;
        this.evidenceRecordRepository = evidenceRecordRepository;
    }

    @Transactional
    public ResearchTask createTask(String query, String language) {
        ResearchTask task = new ResearchTask();
        task.setId(UUID.randomUUID().toString());
        task.setQueryText(query);
        task.setLanguage(language);
        task.setStatus(ResearchTaskStatus.CREATED);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(task.getCreatedAt());
        return researchTaskRepository.save(task);
    }

    public ResearchTask getTask(String taskId) {
        return researchTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found: " + taskId));
    }

    public List<ResearchTask> listTasks() {
        return researchTaskRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<TaskPlanEntity> getTaskPlan(String taskId) {
        return taskPlanRepository.findByTaskId(taskId);
    }

    public Optional<FinalReport> getFinalReport(String taskId) {
        return finalReportRepository.findByTaskId(taskId);
    }

    public long getEvidenceCount(String taskId) {
        return evidenceRecordRepository.findByTaskIdOrderByScoreDescCreatedAtAsc(taskId).size();
    }

    @Transactional
    public ResearchTask markRunning(String taskId) {
        ResearchTask task = getTask(taskId);
        task.setStatus(ResearchTaskStatus.RUNNING);
        task.setStartedAt(Instant.now());
        task.setCompletedAt(null);
        task.setUpdatedAt(task.getStartedAt());
        task.setErrorMessage(null);
        return researchTaskRepository.save(task);
    }

    @Transactional
    public ResearchTask markCompleted(String taskId) {
        ResearchTask task = getTask(taskId);
        task.setStatus(ResearchTaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(task.getCompletedAt());
        return researchTaskRepository.save(task);
    }

    @Transactional
    public ResearchTask markFailed(String taskId, Exception exception) {
        ResearchTask task = getTask(taskId);
        task.setStatus(ResearchTaskStatus.FAILED);
        task.setErrorMessage(exception.getMessage());
        task.setUpdatedAt(Instant.now());
        return researchTaskRepository.save(task);
    }
}
