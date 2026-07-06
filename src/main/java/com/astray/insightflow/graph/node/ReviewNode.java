package com.astray.insightflow.graph.node;

import com.astray.insightflow.agent.reviewer.ReviewResult;
import com.astray.insightflow.agent.reviewer.ReviewerAgent;
import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.agent.writer.ReportSection;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.common.util.MetricsUtils;
import com.astray.insightflow.config.WorkflowProperties;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.report.service.ReportService;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReviewNode {

    private final ReviewerAgent reviewerAgent;
    private final ReportService reportService;
    private final JsonUtils jsonUtils;
    private final WorkflowProperties workflowProperties;
    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;

    public ReviewNode(ReviewerAgent reviewerAgent,
                      ReportService reportService,
                      JsonUtils jsonUtils,
                      WorkflowProperties workflowProperties,
                      TaskProgressPublisher taskProgressPublisher,
                      AgentRunLogService agentRunLogService) {
        this.reviewerAgent = reviewerAgent;
        this.reportService = reportService;
        this.jsonUtils = jsonUtils;
        this.workflowProperties = workflowProperties;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
    }

    public Map<String, Object> execute(ResearchState state) {
        Instant startedAt = Instant.now();
        String taskId = state.taskId();
        taskProgressPublisher.publish(taskId, "review", "RUNNING", "Report review started", Map.of(
                "loopCount", state.loopCount()
        ));
        try {
            String reportJson = jsonUtils.toJson(state.reportDraft());
            String claimsJson = jsonUtils.toJson(Map.of("items", state.claims()));
            String evidenceJson = jsonUtils.toJson(Map.of("items", state.mergedEvidences()));
            ReviewResult reviewResult = reviewerAgent.review(
                    state.userQuery(),
                    state.language(),
                    reportJson,
                    claimsJson,
                    evidenceJson,
                    state.loopCount()
            );

            ReportDraft reviewedDraft = state.reportDraft();
            reviewedDraft.setReviewSummary(reviewResult.getSummary());
            if (reviewResult.isMarkLowConfidence() || state.loopCount() > workflowProperties.maxLoops()) {
                reviewedDraft.setConfidenceNote("低置信度：已达到最大回退次数，报告以部分成功方式输出。");
                for (ReportSection section : reviewedDraft.getSections()) {
                    section.setLowConfidence(true);
                }
            }
            reportService.save(taskId, reviewedDraft);

            int nextLoopCount = state.loopCount();
            if (!reviewResult.isApproved() && state.loopCount() <= workflowProperties.maxLoops()) {
                nextLoopCount = state.loopCount() + 1;
            }

            Map<String, Object> metrics = Map.of(
                    "tokenUsage", MetricsUtils.estimateTokens(reportJson, claimsJson),
                    "citationCoverage", MetricsUtils.citationCoverage(state.claims())
            );

            Map<String, Object> output = new LinkedHashMap<>();
            output.put(ResearchState.REVIEW_RESULT, reviewResult);
            output.put(ResearchState.REPORT_DRAFT, reviewedDraft);
            output.put(ResearchState.LOOP_COUNT, nextLoopCount);
            output.put(ResearchState.STATUS, reviewResult.isApproved() ? "REVIEW_APPROVED" : "REVIEW_RERUN");
            output.put(ResearchState.METRICS, metrics);
            output.put(ResearchState.TIMELINE, List.of("review completed"));
            agentRunLogService.logSuccess(taskId, "review", startedAt, reviewResult, "Review completed", metrics);
            taskProgressPublisher.publish(taskId, "review", "COMPLETED", "Report review completed", Map.of(
                    "approved", reviewResult.isApproved(),
                    "rerunFrom", reviewResult.getRerunFrom().name(),
                    "loopCount", nextLoopCount
            ));
            return output;
        } catch (Exception exception) {
            agentRunLogService.logFailure(taskId, "review", startedAt, exception);
            taskProgressPublisher.publish(taskId, "review", "FAILED", exception.getMessage(), Map.of());
            throw exception;
        }
    }
}
