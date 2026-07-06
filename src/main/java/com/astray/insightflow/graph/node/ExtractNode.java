package com.astray.insightflow.graph.node;

import com.astray.insightflow.agent.extractor.ExtractedFact;
import com.astray.insightflow.agent.extractor.ExtractedFactEntity;
import com.astray.insightflow.agent.extractor.ExtractedFactRepository;
import com.astray.insightflow.agent.extractor.ExtractorAgent;
import com.astray.insightflow.common.util.JsonUtils;
import com.astray.insightflow.common.util.MetricsUtils;
import com.astray.insightflow.graph.state.ResearchState;
import com.astray.insightflow.observe.service.AgentRunLogService;
import com.astray.insightflow.task.service.TaskProgressPublisher;
import com.astray.insightflow.tool.FactNormalizeTool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExtractNode {

    private final ExtractorAgent extractorAgent;
    private final FactNormalizeTool factNormalizeTool;
    private final ExtractedFactRepository extractedFactRepository;
    private final JsonUtils jsonUtils;
    private final TaskProgressPublisher taskProgressPublisher;
    private final AgentRunLogService agentRunLogService;

    public ExtractNode(ExtractorAgent extractorAgent,
                       FactNormalizeTool factNormalizeTool,
                       ExtractedFactRepository extractedFactRepository,
                       JsonUtils jsonUtils,
                       TaskProgressPublisher taskProgressPublisher,
                       AgentRunLogService agentRunLogService) {
        this.extractorAgent = extractorAgent;
        this.factNormalizeTool = factNormalizeTool;
        this.extractedFactRepository = extractedFactRepository;
        this.jsonUtils = jsonUtils;
        this.taskProgressPublisher = taskProgressPublisher;
        this.agentRunLogService = agentRunLogService;
    }

    @Transactional
    public Map<String, Object> execute(ResearchState state) {
        Instant startedAt = Instant.now();
        String taskId = state.taskId();
        taskProgressPublisher.publish(taskId, "extract", "RUNNING", "Fact extraction started", Map.of(
                "evidenceCount", state.mergedEvidences().size()
        ));
        try {
            String planJson = jsonUtils.toJson(state.plan());
            String evidenceJson = jsonUtils.toJson(Map.of("items", state.mergedEvidences()));
            List<ExtractedFact> facts = extractorAgent.extract(state.userQuery(), state.language(), planJson, evidenceJson);
            facts = factNormalizeTool.normalize(taskId, "extract", facts);

            extractedFactRepository.deleteByTaskId(taskId);
            extractedFactRepository.saveAll(facts.stream().map(fact -> toEntity(taskId, fact)).toList());

            Map<String, Object> metrics = Map.of(
                    "tokenUsage", MetricsUtils.estimateTokens(planJson, evidenceJson),
                    "factCount", facts.size()
            );

            Map<String, Object> output = new LinkedHashMap<>();
            output.put(ResearchState.FACTS, facts);
            output.put(ResearchState.STATUS, "FACTS_READY");
            output.put(ResearchState.METRICS, metrics);
            output.put(ResearchState.TIMELINE, List.of("extract completed"));
            agentRunLogService.logSuccess(taskId, "extract", startedAt, facts, "Structured facts extracted", metrics);
            taskProgressPublisher.publish(taskId, "extract", "COMPLETED", "Fact extraction completed", Map.of(
                    "factCount", facts.size()
            ));
            return output;
        } catch (Exception exception) {
            agentRunLogService.logFailure(taskId, "extract", startedAt, exception);
            taskProgressPublisher.publish(taskId, "extract", "FAILED", exception.getMessage(), Map.of());
            throw exception;
        }
    }

    private ExtractedFactEntity toEntity(String taskId, ExtractedFact fact) {
        ExtractedFactEntity entity = new ExtractedFactEntity();
        entity.setId(fact.getId());
        entity.setTaskId(taskId);
        entity.setDimensionName(fact.getDimension());
        entity.setSubjectText(fact.getSubject());
        entity.setAttributeName(fact.getAttribute());
        entity.setValueText(fact.getValue());
        entity.setNormalizedValue(fact.getNormalizedValue());
        entity.setEvidenceId(fact.getEvidenceId());
        entity.setConfidenceScore(fact.getConfidence());
        entity.setCreatedAt(Instant.now());
        return entity;
    }
}
