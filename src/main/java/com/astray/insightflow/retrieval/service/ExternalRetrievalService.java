package com.astray.insightflow.retrieval.service;

import com.astray.insightflow.retrieval.domain.EvidenceRecord;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.tool.WebFetchTool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExternalRetrievalService {

    private final WebFetchTool webFetchTool;
    private final EvidenceRecordRepository evidenceRecordRepository;

    public ExternalRetrievalService(WebFetchTool webFetchTool, EvidenceRecordRepository evidenceRecordRepository) {
        this.webFetchTool = webFetchTool;
        this.evidenceRecordRepository = evidenceRecordRepository;
    }

    @Transactional
    public List<Evidence> search(String taskId, List<String> queries, boolean enabled) {
        if (!enabled || queries == null || queries.isEmpty()) {
            return List.of();
        }

        List<Evidence> result = new ArrayList<>();
        int limit = Math.min(2, queries.size());
        for (int index = 0; index < limit; index++) {
            String query = queries.get(index);
            String url = "https://example.com/research/" + Math.abs(query.hashCode());
            String fetched = webFetchTool.fetch(taskId, "retrieveExternal", url);

            Evidence evidence = new Evidence();
            evidence.setId(UUID.randomUUID().toString());
            evidence.setTitle(query + " - 外部网页补证");
            evidence.setSnippet(fetched);
            evidence.setUrl(url);
            evidence.setSourceType(EvidenceSourceType.EXTERNAL);
            evidence.setScore(Math.max(0.55D, 0.80D - (index * 0.08D)));
            result.add(evidence);
        }
        evidenceRecordRepository.saveAll(result.stream().map(evidence -> toRecord(taskId, evidence)).toList());
        return result;
    }

    private EvidenceRecord toRecord(String taskId, Evidence evidence) {
        EvidenceRecord record = new EvidenceRecord();
        record.setId(evidence.getId());
        record.setTaskId(taskId);
        record.setSourceType(evidence.getSourceType());
        record.setTitle(evidence.getTitle());
        record.setSnippet(evidence.getSnippet());
        record.setUrl(evidence.getUrl());
        record.setDocumentId(evidence.getDocumentId());
        record.setChunkId(evidence.getChunkId());
        record.setScore(evidence.getScore());
        record.setCreatedAt(Instant.now());
        return record;
    }
}
