package com.astray.insightflow.graph.state;

import com.astray.insightflow.agent.planner.PlanResult;
import com.astray.insightflow.agent.reviewer.ReviewResult;
import com.astray.insightflow.agent.verifier.VerifiedClaim;
import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ResearchStateFactoryTests {

    private final ResearchStateFactory researchStateFactory = new ResearchStateFactory(new ObjectMapper());

    @Test
    void coercePatchConvertsNestedMapsToTypedStateObjects() {
        Map<String, Object> patch = Map.of(
                ResearchState.PLAN, Map.of(
                        "objectiveSummary", "分析新能源汽车竞争",
                        "subQueries", List.of("比亚迪 市占率", "特斯拉 定价")
                ),
                ResearchState.MERGED_EVIDENCES, List.of(Map.of(
                        "id", "e-1",
                        "title", "Test Evidence",
                        "snippet", "snippet",
                        "sourceType", "INTERNAL",
                        "score", 0.91D
                )),
                ResearchState.CLAIMS, List.of(Map.of(
                        "id", "c-1",
                        "dimension", "市场格局",
                        "claimText", "比亚迪保持领先",
                        "supportingEvidenceIds", List.of("e-1"),
                        "confidenceScore", 0.88D,
                        "status", "SUPPORTED"
                )),
                ResearchState.REPORT_DRAFT, Map.of(
                        "title", "阶段性报告",
                        "confidenceNote", "高置信度"
                ),
                ResearchState.REVIEW_RESULT, Map.of(
                        "approved", true,
                        "summary", "审查通过"
                )
        );

        Map<String, Object> typed = researchStateFactory.coercePatch(patch);
        ResearchState state = researchStateFactory.apply(typed);

        assertInstanceOf(PlanResult.class, typed.get(ResearchState.PLAN));
        assertInstanceOf(ReportDraft.class, typed.get(ResearchState.REPORT_DRAFT));
        assertInstanceOf(ReviewResult.class, typed.get(ResearchState.REVIEW_RESULT));
        assertInstanceOf(VerifiedClaim.class, state.claims().get(0));
        assertEquals(EvidenceSourceType.INTERNAL, state.mergedEvidences().get(0).getSourceType());
        assertEquals("阶段性报告", state.reportDraft().getTitle());
        assertEquals("审查通过", state.reviewResult().getSummary());
    }

    @Test
    void summarizeReportsCoreWorkflowCounts() {
        Map<String, Object> stateMap = Map.of(
                ResearchState.STATUS, "VERIFIED",
                ResearchState.LOOP_COUNT, 1,
                ResearchState.SUB_QUERIES, List.of("q1", "q2"),
                ResearchState.INTERNAL_EVIDENCES, List.of(new com.astray.insightflow.retrieval.model.Evidence()),
                ResearchState.EXTERNAL_EVIDENCES, List.of(new com.astray.insightflow.retrieval.model.Evidence()),
                ResearchState.MERGED_EVIDENCES, List.of(
                        new com.astray.insightflow.retrieval.model.Evidence(),
                        new com.astray.insightflow.retrieval.model.Evidence()
                ),
                ResearchState.FACTS, List.of(new com.astray.insightflow.agent.extractor.ExtractedFact()),
                ResearchState.CLAIMS, List.of(new VerifiedClaim())
        );

        Map<String, Object> summary = researchStateFactory.summarize(stateMap);

        assertEquals("VERIFIED", summary.get("status"));
        assertEquals(1, summary.get("loopCount"));
        assertEquals(2, summary.get("subQueryCount"));
        assertEquals(1, summary.get("internalEvidenceCount"));
        assertEquals(1, summary.get("externalEvidenceCount"));
        assertEquals(2, summary.get("mergedEvidenceCount"));
        assertEquals(1, summary.get("factCount"));
        assertEquals(1, summary.get("claimCount"));
    }
}
