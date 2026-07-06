package com.astray.insightflow.agent.writer;

import com.astray.insightflow.agent.planner.PlanDimension;
import com.astray.insightflow.agent.planner.PlanResult;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.retrieval.model.Evidence;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StubWriterAgent implements WriterAgent {

    private final JsonUtils jsonUtils;

    public StubWriterAgent(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    @Override
    public ReportDraft write(String query, String language, String planJson, String evidenceJson) {
        PlanResult plan = jsonUtils.fromJson(planJson, PlanResult.class);
        EvidenceListWrapper evidenceWrapper = jsonUtils.fromJson(evidenceJson, EvidenceListWrapper.class);
        List<Evidence> evidences = evidenceWrapper.getItems();

        ReportDraft draft = new ReportDraft();
        draft.setTitle(query + " 研究简报");
        draft.setExecutiveSummary(buildExecutiveSummary(plan, evidences));

        List<String> topEvidenceIds = evidences.stream()
                .limit(3)
                .map(Evidence::getId)
                .collect(Collectors.toList());

        List<ReportSection> sections = new ArrayList<>();
        for (PlanDimension dimension : plan.getDimensions()) {
            String evidenceText = evidences.stream()
                    .limit(2)
                    .map(evidence -> evidence.getTitle() + "：" + evidence.getSnippet())
                    .collect(Collectors.joining("；"));
            sections.add(new ReportSection(
                    dimension.getName(),
                    dimension.getRationale() + "。当前可用证据包括：" + evidenceText,
                    topEvidenceIds
            ));
        }

        if (sections.isEmpty()) {
            sections.add(new ReportSection("研究发现", "当前暂无足够证据，建议补充知识库文档后再次执行。", topEvidenceIds));
        }

        draft.setSections(sections);
        draft.setClosingSummary("本草稿为 Phase 1 自动生成版本，后续可接入事实抽取、claim 验证与审查回退。");
        draft.setConfidenceNote(evidences.isEmpty() ? "低置信度：暂无内部证据命中。" : "中等置信度：已基于内部证据生成首版报告。");
        return draft;
    }

    private String buildExecutiveSummary(PlanResult plan, List<Evidence> evidences) {
        String dimensionSummary = plan.getDimensions().stream()
                .map(PlanDimension::getName)
                .collect(Collectors.joining("、"));
        if (dimensionSummary.isBlank()) {
            dimensionSummary = "核心维度";
        }
        return "本次研究围绕 " + dimensionSummary + " 展开，当前整合 " + evidences.size() + " 条证据用于生成首版分析。";
    }

    public static class EvidenceListWrapper {

        private List<Evidence> items = new ArrayList<>();

        public List<Evidence> getItems() {
            return items;
        }

        public void setItems(List<Evidence> items) {
            this.items = items == null ? new ArrayList<>() : items;
        }
    }
}
