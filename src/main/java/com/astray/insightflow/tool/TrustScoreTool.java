package com.astray.insightflow.tool;

import com.astray.insightflow.observe.service.ToolCallLogService;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TrustScoreTool {

    private final ToolCallLogService toolCallLogService;

    public TrustScoreTool(ToolCallLogService toolCallLogService) {
        this.toolCallLogService = toolCallLogService;
    }

    @Tool("Scores evidence source trustworthiness")
    public Map<String, Double> scoreBatch(String taskId, String nodeName, List<Evidence> evidences) {
        Instant startedAt = Instant.now();
        try {
            Map<String, Double> scores = new LinkedHashMap<>();
            for (Evidence evidence : evidences) {
                scores.put(evidence.getId(), score(evidence));
            }
            toolCallLogService.logSuccess(taskId, nodeName, "TrustScoreTool", startedAt,
                    Map.of("evidenceCount", evidences.size()), scores, Map.of("evidenceCount", evidences.size()));
            return scores;
        } catch (Exception exception) {
            toolCallLogService.logFailure(taskId, nodeName, "TrustScoreTool", startedAt,
                    Map.of("evidenceCount", evidences.size()), exception);
            throw exception;
        }
    }

    public double score(Evidence evidence) {
        if (evidence.getSourceType() == EvidenceSourceType.INTERNAL) {
            return 0.92D;
        }
        String host = hostOf(evidence.getUrl());
        if (!StringUtils.hasText(host)) {
            return 0.64D;
        }
        if (host.endsWith(".gov") || host.endsWith(".edu")) {
            return 0.90D;
        }
        if (host.contains("reuters") || host.contains("bloomberg") || host.contains("marklines")
                || host.contains("sec.gov") || host.contains("investor") || host.contains("ir.")) {
            return 0.84D;
        }
        if (host.contains("news.") || host.contains("finance.") || host.contains("press")) {
            return 0.78D;
        }
        if (host.contains("zhihu") || host.contains("weibo") || host.contains("toutiao") || host.contains("bilibili")) {
            return 0.62D;
        }
        return 0.72D;
    }

    private String hostOf(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            URI uri = new URI(url);
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException exception) {
            return "";
        }
    }
}
