package com.astray.insightflow.agent.extractor;

import com.astray.insightflow.agent.planner.PlanDimension;
import com.astray.insightflow.agent.planner.PlanResult;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.retrieval.model.Evidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class StubExtractorAgent implements ExtractorAgent {

    private final JsonUtils jsonUtils;

    public StubExtractorAgent(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    @Override
    public ExtractResult extract(String query, String language, String planJson, String evidenceJson) {
        PlanResult plan = jsonUtils.fromJson(planJson, PlanResult.class);
        EvidenceListWrapper evidenceWrapper = jsonUtils.fromJson(evidenceJson, EvidenceListWrapper.class);
        List<ExtractedFact> facts = new ArrayList<>();

        for (Evidence evidence : evidenceWrapper.getItems()) {
            List<String> sentences = splitSentences(evidence.getSnippet());
            int limit = Math.min(2, sentences.size());
            for (int index = 0; index < limit; index++) {
                String sentence = sentences.get(index);
                ExtractedFact fact = new ExtractedFact();
                fact.setId(UUID.randomUUID().toString());
                fact.setDimension(inferDimension(sentence, plan.getDimensions()));
                fact.setSubject(inferSubject(sentence, evidence.getTitle()));
                fact.setAttribute(inferAttribute(sentence));
                fact.setValue(sentence);
                fact.setNormalizedValue(normalize(sentence));
                fact.setEvidenceId(evidence.getId());
                fact.setEvidenceSnippet(evidence.getSnippet());
                fact.setSourceType(evidence.getSourceType());
                fact.setConfidence(Math.min(0.92D, 0.58D + (evidence.getScore() * 0.05D)));
                facts.add(fact);
            }
        }
        ExtractResult result = new ExtractResult();
        result.setItems(facts);
        return result;
    }

    private List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] raw = text.replace("...", "。").split("[。！？!?；;]");
        List<String> sentences = new ArrayList<>();
        for (String item : raw) {
            String value = item.trim();
            if (!value.isBlank()) {
                sentences.add(value);
            }
        }
        return sentences.isEmpty() ? List.of(text.trim()) : sentences;
    }

    private String inferDimension(String sentence, List<PlanDimension> dimensions) {
        String lowered = sentence.toLowerCase(Locale.ROOT);
        if (lowered.contains("市场") || lowered.contains("增长") || lowered.contains("规模")) {
            return matchDimension(dimensions, "市场");
        }
        if (lowered.contains("竞争") || lowered.contains("品牌") || lowered.contains("价格")
                || lowered.contains("特斯拉") || lowered.contains("比亚迪")) {
            return matchDimension(dimensions, "竞争");
        }
        if (lowered.contains("建议") || lowered.contains("风险") || lowered.contains("机会")) {
            return matchDimension(dimensions, "结论");
        }
        return dimensions.isEmpty() ? "研究发现" : dimensions.get(0).getName();
    }

    private String matchDimension(List<PlanDimension> dimensions, String keyword) {
        return dimensions.stream()
                .map(PlanDimension::getName)
                .filter(name -> name != null && name.contains(keyword))
                .findFirst()
                .orElse(dimensions.isEmpty() ? "研究发现" : dimensions.get(0).getName());
    }

    private String inferSubject(String sentence, String fallback) {
        if (sentence.contains("比亚迪")) {
            return "比亚迪";
        }
        if (sentence.contains("特斯拉")) {
            return "特斯拉";
        }
        if (sentence.contains("理想")) {
            return "理想汽车";
        }
        return fallback == null || fallback.isBlank() ? "研究对象" : fallback;
    }

    private String inferAttribute(String sentence) {
        if (sentence.contains("增长") || sentence.contains("规模")) {
            return "市场表现";
        }
        if (sentence.contains("优势") || sentence.contains("差异")) {
            return "竞争特点";
        }
        if (sentence.contains("风险") || sentence.contains("承压")) {
            return "风险提示";
        }
        return "关键信息";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
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
