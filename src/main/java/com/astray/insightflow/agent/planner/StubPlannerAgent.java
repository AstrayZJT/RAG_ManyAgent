package com.astray.insightflow.agent.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StubPlannerAgent implements PlannerAgent {

    @Override
    public PlanResult plan(String query, String language) {
        String normalized = query == null ? "" : query.trim();
        String locale = language == null || language.isBlank() ? "zh-CN" : language;

        List<String> keywords = extractKeywords(normalized);
        PlanResult result = new PlanResult();
        result.setObjectiveSummary("围绕研究目标生成可执行的竞品/行业研究提纲，语言=" + locale);
        result.setDimensions(List.of(
                new PlanDimension("市场概况", "界定研究对象、背景与关键驱动力"),
                new PlanDimension("竞争格局", "提炼主要参与者、产品与差异化点"),
                new PlanDimension("结论建议", "归纳当前证据支持的判断与行动建议")
        ));
        result.setSubQueries(List.of(
                normalized,
                normalized + " 市场概况",
                normalized + " 竞争格局",
                normalized + " 关键结论"
        ));
        result.setRetrievalStrategy("先检索内部知识库文档块，再按维度聚合证据；如内部证据稀少则预留外部补证入口。");
        result.setFactSchema(List.of(
                new FactField("company", "涉及的公司或品牌"),
                new FactField("product", "关键产品或方案名称"),
                new FactField("metric", "可量化指标、市场份额、增长率等"),
                new FactField("sourceTime", "证据时间")
        ));
        result.setNeedExternalSearch(keywords.size() <= 2);
        return result;
    }

    private List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String[] tokens = query.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsIdeographic}\\p{IsDigit}\\s]", " ")
                .trim()
                .split("\\s+");
        List<String> keywords = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isBlank() && keywords.stream().noneMatch(token::equals)) {
                keywords.add(token);
            }
        }
        return keywords;
    }
}
