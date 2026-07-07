package com.astray.insightflow.graph.node;

import com.astray.insightflow.agent.extractor.ExtractResult;
import com.astray.insightflow.agent.extractor.ExtractedFact;
import com.astray.insightflow.agent.extractor.ExtractedFactRepository;
import com.astray.insightflow.agent.extractor.ExtractorAgent;
import com.astray.insightflow.agent.planner.PlanDimension;
import com.astray.insightflow.agent.planner.PlanResult;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.tool.FactNormalizeTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtractNodeTests {

    @Test
    @SuppressWarnings("unchecked")
    void executeFallsBackToHeuristicExtractionWhenLlmReturnsEmptyFacts() {
        ExtractorAgent extractorAgent = mock(ExtractorAgent.class);
        when(extractorAgent.extract(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ExtractResult());

        FactNormalizeTool factNormalizeTool = mock(FactNormalizeTool.class);
        when(factNormalizeTool.normalize(anyString(), eq("extract"), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        ExtractedFactRepository extractedFactRepository = mock(ExtractedFactRepository.class);
        JsonUtils jsonUtils = new JsonUtils(new ObjectMapper());
        TaskProgressPublisher taskProgressPublisher = mock(TaskProgressPublisher.class);
        AgentRunLogService agentRunLogService = mock(AgentRunLogService.class);

        ExtractNode node = new ExtractNode(
                extractorAgent,
                factNormalizeTool,
                extractedFactRepository,
                jsonUtils,
                taskProgressPublisher,
                agentRunLogService
        );

        PlanResult plan = new PlanResult();
        plan.setDimensions(List.of(new PlanDimension("发展历史分期与关键事件", "用于承接时间轴事实")));

        Evidence evidence = new Evidence(
                "e-1",
                "国家电网新闻",
                "国家电网持续推进特高压建设。2024年继续加强跨区输电能力。",
                "https://example.com/grid",
                EvidenceSourceType.EXTERNAL,
                null,
                null,
                0.92D
        );

        Map<String, Object> initData = new HashMap<>();
        initData.put(ResearchState.TASK_ID, "task-1");
        initData.put(ResearchState.USER_QUERY, "调研中国电网发展历史与现状");
        initData.put(ResearchState.LANGUAGE, "zh-CN");
        initData.put(ResearchState.PLAN, plan);
        initData.put(ResearchState.MERGED_EVIDENCES, List.of(evidence));

        ResearchState state = new ResearchState(initData);
        Map<String, Object> output = node.execute(state);

        List<ExtractedFact> facts = (List<ExtractedFact>) output.get(ResearchState.FACTS);
        Map<String, Object> metrics = (Map<String, Object>) output.get(ResearchState.METRICS);

        assertFalse(facts.isEmpty());
        assertTrue(facts.stream().allMatch(fact -> "e-1".equals(fact.getEvidenceId())));
        assertTrue(Boolean.TRUE.equals(metrics.get("fallbackUsed")));

        verify(extractedFactRepository).deleteByTaskId("task-1");
        verify(extractedFactRepository).saveAll(anyList());
        verify(factNormalizeTool).normalize(eq("task-1"), eq("extract"), anyList());
        verify(taskProgressPublisher).publish(eq("task-1"), eq("extract"), eq("COMPLETED"), anyString(), any(Map.class));
    }
}
