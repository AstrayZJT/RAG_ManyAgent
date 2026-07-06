package com.astray.insightflow.graph.state;

import com.astray.insightflow.agent.extractor.ExtractedFact;
import com.astray.insightflow.agent.planner.PlanResult;
import com.astray.insightflow.agent.reviewer.ReviewResult;
import com.astray.insightflow.agent.verifier.VerifiedClaim;
import com.astray.insightflow.agent.verifier.VerifyDecision;
import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.task.domain.ResearchTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResearchStateFactory implements AgentStateFactory<ResearchState> {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Evidence>> EVIDENCE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ExtractedFact>> FACT_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<VerifiedClaim>> CLAIM_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ResearchStateFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ResearchState apply(Map<String, Object> initData) {
        return new ResearchState(coerceStateMap(initData));
    }

    public Map<String, Object> initialState(ResearchTask task) {
        LinkedHashMap<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(ResearchState.TASK_ID, task.getId());
        inputs.put(ResearchState.USER_QUERY, task.getQueryText());
        inputs.put(ResearchState.LANGUAGE, task.getLanguage());
        inputs.put(ResearchState.LOOP_COUNT, 0);
        inputs.put(ResearchState.STATUS, "RUNNING");
        return inputs;
    }

    public Map<String, Object> coercePatch(Map<String, Object> patch) {
        return coerceStateMap(patch);
    }

    public Map<String, Object> coerceStateMap(Map<String, Object> rawState) {
        LinkedHashMap<String, Object> typedState = new LinkedHashMap<>();
        if (rawState == null || rawState.isEmpty()) {
            return typedState;
        }

        rawState.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            switch (key) {
                case ResearchState.TASK_ID, ResearchState.USER_QUERY, ResearchState.LANGUAGE, ResearchState.STATUS ->
                        typedState.put(key, convert(value, String.class));
                case ResearchState.PLAN -> typedState.put(key, convert(value, PlanResult.class));
                case ResearchState.SUB_QUERIES, ResearchState.TIMELINE ->
                        typedState.put(key, convert(value, STRING_LIST_TYPE));
                case ResearchState.NEED_EXTERNAL_SEARCH -> typedState.put(key, convert(value, Boolean.class));
                case ResearchState.INTERNAL_EVIDENCES, ResearchState.EXTERNAL_EVIDENCES, ResearchState.MERGED_EVIDENCES ->
                        typedState.put(key, convert(value, EVIDENCE_LIST_TYPE));
                case ResearchState.FACTS -> typedState.put(key, convert(value, FACT_LIST_TYPE));
                case ResearchState.CLAIMS -> typedState.put(key, convert(value, CLAIM_LIST_TYPE));
                case ResearchState.VERIFY_DECISION -> typedState.put(key, convert(value, VerifyDecision.class));
                case ResearchState.REPORT_DRAFT -> typedState.put(key, convert(value, ReportDraft.class));
                case ResearchState.REVIEW_RESULT -> typedState.put(key, convert(value, ReviewResult.class));
                case ResearchState.LOOP_COUNT -> typedState.put(key, convert(value, Integer.class));
                case ResearchState.METRICS -> typedState.put(key, convert(value, MAP_TYPE));
                default -> typedState.put(key, value);
            }
        });
        return typedState;
    }

    public String toStateJson(Map<String, Object> stateMap) {
        try {
            return objectMapper.writeValueAsString(toApiState(stateMap));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize research state", exception);
        }
    }

    public Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize state JSON", exception);
        }
    }

    public Map<String, Object> fromStateJson(String json) {
        return coerceStateMap(readJsonMap(json));
    }

    public Map<String, Object> toApiState(Map<String, Object> stateMap) {
        return objectMapper.convertValue(coerceStateMap(stateMap), MAP_TYPE);
    }

    public Map<String, Object> summarize(Map<String, Object> stateMap) {
        ResearchState state = apply(stateMap);
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", state.status());
        summary.put("loopCount", state.loopCount());
        summary.put("needExternalSearch", state.needExternalSearch());
        summary.put("subQueryCount", state.subQueries().size());
        summary.put("internalEvidenceCount", state.internalEvidences().size());
        summary.put("externalEvidenceCount", state.externalEvidences().size());
        summary.put("mergedEvidenceCount", state.mergedEvidences().size());
        summary.put("factCount", state.facts().size());
        summary.put("claimCount", state.claims().size());
        summary.put("timelineCount", state.timeline().size());
        if (StringUtils.hasText(state.reportDraft().getTitle())) {
            summary.put("reportTitle", state.reportDraft().getTitle());
        }
        if (StringUtils.hasText(state.reviewResult().getSummary())) {
            summary.put("reviewApproved", state.reviewResult().isApproved());
        }
        if (!state.metrics().isEmpty()) {
            summary.put("metrics", state.metrics());
        }
        return summary;
    }

    private <T> T convert(Object value, Class<T> type) {
        return objectMapper.convertValue(value, type);
    }

    private <T> T convert(Object value, TypeReference<T> type) {
        return objectMapper.convertValue(value, type);
    }
}
