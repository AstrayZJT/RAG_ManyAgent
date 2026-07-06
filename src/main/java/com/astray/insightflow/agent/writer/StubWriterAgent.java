package com.astray.insightflow.agent.writer;

import com.astray.insightflow.agent.planner.PlanDimension;
import com.astray.insightflow.agent.planner.PlanResult;
import com.astray.insightflow.agent.verifier.VerifiedClaim;
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
    public ReportDraft write(String query, String language, String planJson, String claimsJson, String evidenceJson) {
        PlanResult plan = jsonUtils.fromJson(planJson, PlanResult.class);
        ClaimListWrapper claimsWrapper = jsonUtils.fromJson(claimsJson, ClaimListWrapper.class);
        EvidenceListWrapper evidenceWrapper = jsonUtils.fromJson(evidenceJson, EvidenceListWrapper.class);

        List<VerifiedClaim> claims = claimsWrapper.getItems();
        List<Evidence> evidences = evidenceWrapper.getItems();

        ReportDraft draft = new ReportDraft();
        draft.setTitle(query + " 研究简报");
        draft.setExecutiveSummary(buildExecutiveSummary(plan, evidences, claims));

        List<String> topEvidenceIds = evidences.stream()
                .limit(3)
                .map(Evidence::getId)
                .collect(Collectors.toList());

        List<ReportSection> sections = new ArrayList<>();
        for (PlanDimension dimension : plan.getDimensions()) {
            List<VerifiedClaim> dimensionClaims = claims.stream()
                    .filter(claim -> dimension.getName().equals(claim.getDimension()))
                    .toList();
            String claimSummary = dimensionClaims.stream()
                    .map(VerifiedClaim::getClaimText)
                    .collect(Collectors.joining("；"));
            if (claimSummary.isBlank()) {
                claimSummary = "当前还没有形成足够稳定的 claim。";
            }
            String evidenceText = evidences.stream()
                    .filter(evidence -> dimensionClaims.stream()
                            .flatMap(claim -> claim.getSupportingEvidenceIds().stream())
                            .anyMatch(evidence.getId()::equals))
                    .limit(2)
                    .map(evidence -> evidence.getTitle() + "：" + evidence.getSnippet())
                    .collect(Collectors.joining("；"));
            if (evidenceText.isBlank()) {
                evidenceText = "暂无直接证据。";
            }

            ReportSection section = new ReportSection(
                    dimension.getName(),
                    dimension.getRationale() + "。核心判断：" + claimSummary + "。当前可用证据包括：" + evidenceText,
                    dimensionClaims.stream()
                            .flatMap(claim -> claim.getSupportingEvidenceIds().stream())
                            .distinct()
                            .toList()
            );
            section.setLowConfidence(dimensionClaims.isEmpty() || dimensionClaims.stream().anyMatch(VerifiedClaim::isLowConfidence));
            sections.add(section);
        }

        if (sections.isEmpty()) {
            ReportSection fallback = new ReportSection(
                    "研究发现",
                    "当前暂无足够证据，建议补充知识库文档后再次执行。",
                    topEvidenceIds
            );
            fallback.setLowConfidence(true);
            sections.add(fallback);
        }

        draft.setSections(sections);
        draft.setClosingSummary("本草稿为 Phase 2 结构化生成版本，后续可继续接入 checkpoint、评测和人工介入。");
        draft.setConfidenceNote(buildConfidenceNote(plan, evidences, claims));
        draft.setReviewSummary("待审查");
        return draft;
    }

    private String buildExecutiveSummary(PlanResult plan, List<Evidence> evidences, List<VerifiedClaim> claims) {
        String dimensionSummary = plan.getDimensions().stream()
                .map(PlanDimension::getName)
                .collect(Collectors.joining("、"));
        if (dimensionSummary.isBlank()) {
            dimensionSummary = "核心维度";
        }
        long supportedClaims = claims.stream().filter(claim -> !claim.isLowConfidence()).count();
        return "本次研究围绕 " + dimensionSummary + " 展开，当前整合 " + evidences.size()
                + " 条证据并形成 " + claims.size() + " 个 claim，其中 "
                + supportedClaims + " 个达到较高支持度。";
    }

    private String buildConfidenceNote(PlanResult plan, List<Evidence> evidences, List<VerifiedClaim> claims) {
        if (evidences.isEmpty()) {
            return "低置信度：暂无证据命中。";
        }
        long lowConfidenceClaims = claims.stream().filter(VerifiedClaim::isLowConfidence).count();
        if (claims.size() < plan.getDimensions().size()) {
            return "中等置信度：部分维度尚未形成稳定 claim，报告已标记低置信度章节。";
        }
        if (lowConfidenceClaims > 0) {
            return "中等置信度：已有证据支持，但仍存在 " + lowConfidenceClaims + " 个低置信度 claim。";
        }
        return "较高置信度：主要结论已绑定支持证据。";
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

    public static class ClaimListWrapper {

        private List<VerifiedClaim> items = new ArrayList<>();

        public List<VerifiedClaim> getItems() {
            return items;
        }

        public void setItems(List<VerifiedClaim> items) {
            this.items = items == null ? new ArrayList<>() : items;
        }
    }
}
