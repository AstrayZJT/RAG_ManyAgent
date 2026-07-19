package com.astray.insightflow.graph.node;

import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.agent.writer.WriterAgent;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.common.util.MetricsUtils;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.report.service.ReportService;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.tool.CitationGuardTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WriteNode {

    private final WriterAgent writerAgent;
    private final ReportService reportService;
    private final CitationGuardTool citationGuardTool;
    private final JsonUtils jsonUtils;
    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;

    public WriteNode(WriterAgent writerAgent,
                     ReportService reportService,
                     CitationGuardTool citationGuardTool,
                     JsonUtils jsonUtils,
                     TaskProgressPublisher taskProgressPublisher,
                     AgentRunLogService agentRunLogService) {
        this.writerAgent = writerAgent;
        this.reportService = reportService;
        this.citationGuardTool = citationGuardTool;
        this.jsonUtils = jsonUtils;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
    }

    public Map<String, Object> execute(ResearchState state) {
        Instant startedAt = Instant.now();
        String taskId = state.taskId();
        taskProgressPublisher.publish(taskId, "write", "RUNNING", "Writer node started", Map.of(
                "claimCount", state.claims().size()
        ));
        try {
            String planJson = jsonUtils.toJson(state.plan());
            String claimsJson = jsonUtils.toJson(Map.of("items", state.claims()));
            String evidenceJson = jsonUtils.toJson(Map.of("items", state.mergedEvidences()));
            ReportDraft draft = writerAgent.write(state.userQuery(), state.language(), planJson, claimsJson, evidenceJson);
            CitationGuardTool.ReportValidationResult citationValidation = citationGuardTool.validateReportDraft(
                    taskId,
                    "write",
                    draft,
                    state.mergedEvidences()
            );
            draft = citationValidation.draft();
            reportService.save(taskId, draft);

            Map<String, Object> metrics = Map.of(
                    "tokenUsage", MetricsUtils.estimateTokens(planJson, claimsJson, evidenceJson),
                    "citationCoverage", MetricsUtils.citationCoverage(state.claims()),
                    "invalidCitationCount", citationValidation.invalidCitationCount(),
                    "unsupportedSectionCount", citationValidation.unsupportedSectionCount()
            );

            Map<String, Object> output = new LinkedHashMap<>();
            output.put(ResearchState.REPORT_DRAFT, draft);
            output.put(ResearchState.STATUS, "REPORT_DRAFTED");
            output.put(ResearchState.METRICS, metrics);
            output.put(ResearchState.TIMELINE, List.of("write completed"));
            agentRunLogService.logSuccess(taskId, "write", startedAt, draft, "Writer produced report draft", metrics);
            taskProgressPublisher.publish(taskId, "write", "COMPLETED", "Writer node completed", Map.of(
                    "title", draft.getTitle()
            ));
            return output;
        } catch (Exception exception) {
            agentRunLogService.logFailure(taskId, "write", startedAt, exception);
            taskProgressPublisher.publish(taskId, "write", "FAILED", exception.getMessage(), Map.of());
            throw exception;
        }
    }
}
