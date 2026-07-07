package com.astray.insightflow.agent.planner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerPlanTemplatesTests {

    @Test
    void normalizeReplacesGenericPlannerOutputWithTopicAnchoredFallback() {
        PlanResult genericPlan = new PlanResult();
        genericPlan.setObjectiveSummary("围绕指定研究目标生成综合调研框架");
        genericPlan.setDimensions(List.of(
                new PlanDimension("市场概况", "描述研究背景"),
                new PlanDimension("竞争格局", "描述主要参与者"),
                new PlanDimension("结论建议", "给出总结")
        ));
        genericPlan.setSubQueries(List.of(
                "研究主题 市场概况",
                "研究主题 竞争格局",
                "研究主题 关键事实",
                "研究主题 结论建议"
        ));
        genericPlan.setRetrievalStrategy("先检索内部文档，再视情况补充。");
        genericPlan.setFactSchema(List.of(
                new FactField("company", "公司"),
                new FactField("product", "产品"),
                new FactField("metric", "指标"),
                new FactField("sourceTime", "时间")
        ));
        genericPlan.setNeedExternalSearch(false);

        PlanResult normalized = PlannerPlanTemplates.normalize("调研中国电网发展历史与现状", "zh-CN", genericPlan);

        assertTrue(normalized.getObjectiveSummary().contains("中国电网"));
        assertEquals("发展历史分期与关键事件", normalized.getDimensions().get(0).getName());
        assertTrue(normalized.getSubQueries().stream().anyMatch(query -> query.contains("特高压")));
        assertTrue(normalized.isNeedExternalSearch());
    }

    @Test
    void normalizePreservesSpecificPlanAndTurnsOnExternalSearchForRecentTopic() {
        PlanResult specificPlan = new PlanResult();
        specificPlan.setObjectiveSummary("梳理储能行业 2024 年竞争格局与政策变化");
        specificPlan.setDimensions(List.of(
                new PlanDimension("市场格局", "识别主要玩家与份额变化"),
                new PlanDimension("政策变化", "核查近年的监管与补贴政策"),
                new PlanDimension("财务表现", "比较营收增速与盈利能力")
        ));
        specificPlan.setSubQueries(List.of(
                "储能行业 2024 市场份额 变化趋势",
                "储能行业 2024 政策 监管 补贴",
                "储能行业 主要企业 营收 毛利",
                "储能行业 竞品 排名 差异化"
        ));
        specificPlan.setRetrievalStrategy("先查内部行业库，再对 2024 年新信息补外部证据。");
        specificPlan.setFactSchema(List.of(
                new FactField("marketShare", "主要企业市场份额"),
                new FactField("policyName", "关键政策名称"),
                new FactField("revenueGrowth", "营收增速"),
                new FactField("grossMargin", "毛利率")
        ));
        specificPlan.setNeedExternalSearch(false);

        PlanResult normalized = PlannerPlanTemplates.normalize("储能行业 2024 年竞争格局与政策变化", "zh-CN", specificPlan);

        assertEquals("梳理储能行业 2024 年竞争格局与政策变化", normalized.getObjectiveSummary());
        assertFalse(normalized.getDimensions().isEmpty());
        assertTrue(normalized.isNeedExternalSearch());
    }
}
