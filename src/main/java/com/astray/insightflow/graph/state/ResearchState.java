package com.astray.insightflow.graph.state;

import com.astray.insightflow.agent.planner.PlanResult;
import com.astray.insightflow.agent.writer.ReportDraft;
import com.astray.insightflow.retrieval.model.Evidence;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

public class ResearchState extends AgentState implements Serializable {

    public static final String TASK_ID = "taskId";
    public static final String USER_QUERY = "userQuery";
    public static final String LANGUAGE = "language";
    public static final String PLAN = "plan";
    public static final String SUB_QUERIES = "subQueries";
    public static final String NEED_EXTERNAL_SEARCH = "needExternalSearch";
    public static final String INTERNAL_EVIDENCES = "internalEvidences";
    public static final String EXTERNAL_EVIDENCES = "externalEvidences";
    public static final String MERGED_EVIDENCES = "mergedEvidences";
    public static final String REPORT_DRAFT = "reportDraft";
    public static final String STATUS = "status";
    public static final String METRICS = "metrics";
    public static final String TIMELINE = "timeline";

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            entry(TASK_ID, Channels.base((current, incoming) -> incoming)),
            entry(USER_QUERY, Channels.base((current, incoming) -> incoming)),
            entry(LANGUAGE, Channels.base((current, incoming) -> incoming)),
            entry(PLAN, Channels.base((current, incoming) -> incoming)),
            entry(SUB_QUERIES, Channels.base((current, incoming) -> incoming)),
            entry(NEED_EXTERNAL_SEARCH, Channels.base((current, incoming) -> incoming)),
            entry(INTERNAL_EVIDENCES, Channels.base((current, incoming) -> incoming)),
            entry(EXTERNAL_EVIDENCES, Channels.base((current, incoming) -> incoming)),
            entry(MERGED_EVIDENCES, Channels.base((current, incoming) -> incoming)),
            entry(REPORT_DRAFT, Channels.base((current, incoming) -> incoming)),
            entry(STATUS, Channels.base((current, incoming) -> incoming)),
            entry(METRICS, Channels.base(ResearchState::mergeMetrics)),
            entry(TIMELINE, Channels.base(ResearchState::mergeTimeline))
    );

    public ResearchState(Map<String, Object> initData) {
        super(initData);
    }

    public String taskId() {
        return this.<String>value(TASK_ID).orElse("");
    }

    public String userQuery() {
        return this.<String>value(USER_QUERY).orElse("");
    }

    public String language() {
        return this.<String>value(LANGUAGE).orElse("zh-CN");
    }

    public PlanResult plan() {
        return this.<PlanResult>value(PLAN).orElse(new PlanResult());
    }

    public List<String> subQueries() {
        return this.<List<String>>value(SUB_QUERIES).orElseGet(ArrayList::new);
    }

    public boolean needExternalSearch() {
        return this.<Boolean>value(NEED_EXTERNAL_SEARCH).orElse(Boolean.FALSE);
    }

    public List<Evidence> internalEvidences() {
        return this.<List<Evidence>>value(INTERNAL_EVIDENCES).orElseGet(ArrayList::new);
    }

    public List<Evidence> externalEvidences() {
        return this.<List<Evidence>>value(EXTERNAL_EVIDENCES).orElseGet(ArrayList::new);
    }

    public List<Evidence> mergedEvidences() {
        return this.<List<Evidence>>value(MERGED_EVIDENCES).orElseGet(ArrayList::new);
    }

    public ReportDraft reportDraft() {
        return this.<ReportDraft>value(REPORT_DRAFT).orElse(new ReportDraft());
    }

    public String status() {
        return this.<String>value(STATUS).orElse("CREATED");
    }

    public Map<String, Object> metrics() {
        return this.<Map<String, Object>>value(METRICS).orElseGet(HashMap::new);
    }

    public List<String> timeline() {
        return this.<List<String>>value(TIMELINE).orElseGet(ArrayList::new);
    }

    private static Map<String, Object> mergeMetrics(Map<String, Object> current, Map<String, Object> incoming) {
        Map<String, Object> merged = new HashMap<>();
        if (current != null) {
            merged.putAll(current);
        }
        if (incoming != null) {
            merged.putAll(incoming);
        }
        return merged;
    }

    private static List<String> mergeTimeline(List<String> current, List<String> incoming) {
        List<String> merged = new ArrayList<>();
        if (current != null) {
            merged.addAll(current);
        }
        if (incoming != null) {
            merged.addAll(incoming);
        }
        return merged;
    }
}
