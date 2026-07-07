package com.astray.insightflow.report.service;

import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.agent.writer.ReportSection;
import com.astray.insightflow.common.exception.NotFoundException;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.report.domain.FinalReport;
import com.astray.insightflow.report.persistence.FinalReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ReportService {

    private final FinalReportRepository finalReportRepository;
    private final JsonUtils jsonUtils;

    public ReportService(FinalReportRepository finalReportRepository, JsonUtils jsonUtils) {
        this.finalReportRepository = finalReportRepository;
        this.jsonUtils = jsonUtils;
    }

    @Transactional
    public FinalReport save(String taskId, ReportDraft draft) {
        FinalReport report = finalReportRepository.findByTaskId(taskId).orElseGet(FinalReport::new);
        if (report.getId() == null) {
            report.setId(UUID.randomUUID().toString());
            report.setTaskId(taskId);
            report.setCreatedAt(Instant.now());
        }
        report.setTitle(draft.getTitle());
        report.setReportJson(jsonUtils.toJson(draft));
        report.setReportMarkdown(toMarkdown(draft));
        report.setUpdatedAt(Instant.now());
        return finalReportRepository.save(report);
    }

    public FinalReport getByTaskId(String taskId) {
        return finalReportRepository.findByTaskId(taskId)
                .orElseThrow(() -> new NotFoundException("Final report not found for task: " + taskId));
    }

    private String toMarkdown(ReportDraft draft) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(draft.getTitle()).append("\n\n");
        builder.append("## 执行摘要\n");
        builder.append(draft.getExecutiveSummary()).append("\n\n");

        for (ReportSection section : draft.getSections()) {
            builder.append("## ").append(section.getHeading());
            if (section.isLowConfidence()) {
                builder.append(" [低置信度]");
            }
            builder.append("\n");
            builder.append(section.getContent()).append("\n");
            if (!section.getEvidenceIds().isEmpty()) {
                builder.append("引用：").append(String.join(", ", section.getEvidenceIds())).append("\n");
            }
            builder.append("\n");
        }

        builder.append("## 结论与建议\n");
        builder.append(draft.getClosingSummary()).append("\n\n");
        builder.append("置信度说明：").append(draft.getConfidenceNote()).append("\n");
        if (draft.getReviewSummary() != null && !draft.getReviewSummary().isBlank()) {
            builder.append("审查结论：").append(draft.getReviewSummary()).append("\n");
        }
        return builder.toString();
    }
}
