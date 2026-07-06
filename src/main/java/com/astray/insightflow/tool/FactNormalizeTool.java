package com.astray.insightflow.tool;

import com.astray.insightflow.agent.extractor.ExtractedFact;
import com.astray.insightflow.observe.service.ToolCallLogService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class FactNormalizeTool {

    private final ToolCallLogService toolCallLogService;

    public FactNormalizeTool(ToolCallLogService toolCallLogService) {
        this.toolCallLogService = toolCallLogService;
    }

    @Tool("Normalizes extracted fact values and whitespace")
    public List<ExtractedFact> normalize(String taskId, String nodeName, List<ExtractedFact> facts) {
        Instant startedAt = Instant.now();
        try {
            for (ExtractedFact fact : facts) {
                if (fact.getValue() != null) {
                    fact.setValue(fact.getValue().trim().replaceAll("\\s+", " "));
                }
                if (fact.getNormalizedValue() != null) {
                    fact.setNormalizedValue(fact.getNormalizedValue().trim().replaceAll("\\s+", " "));
                }
                if (fact.getSubject() != null) {
                    fact.setSubject(fact.getSubject().trim());
                }
                if (fact.getAttribute() != null) {
                    fact.setAttribute(fact.getAttribute().trim());
                }
            }
            toolCallLogService.logSuccess(taskId, nodeName, "FactNormalizeTool", startedAt,
                    Map.of("factCount", facts.size()), facts, Map.of("factCount", facts.size()));
            return facts;
        } catch (Exception exception) {
            toolCallLogService.logFailure(taskId, nodeName, "FactNormalizeTool", startedAt,
                    Map.of("factCount", facts.size()), exception);
            throw exception;
        }
    }
}
