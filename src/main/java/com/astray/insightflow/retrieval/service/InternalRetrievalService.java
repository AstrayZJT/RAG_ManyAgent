package com.astray.insightflow.retrieval.service;

import com.astray.insightflow.config.AgentProperties;
import com.astray.insightflow.knowledge.domain.DocumentChunk;
import com.astray.insightflow.knowledge.domain.KnowledgeDocument;
import com.astray.insightflow.knowledge.persistence.DocumentChunkRepository;
import com.astray.insightflow.knowledge.persistence.KnowledgeDocumentRepository;
import com.astray.insightflow.retrieval.domain.EvidenceRecord;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class InternalRetrievalService {

    private final DocumentChunkRepository documentChunkRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final int maxResults;

    public InternalRetrievalService(DocumentChunkRepository documentChunkRepository,
                                    KnowledgeDocumentRepository knowledgeDocumentRepository,
                                    EvidenceRecordRepository evidenceRecordRepository,
                                    AgentProperties agentProperties) {
        this.documentChunkRepository = documentChunkRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.maxResults = agentProperties.search().maxResults();
    }

    @Transactional
    public List<Evidence> search(String taskId, List<String> queries) {
        Map<String, KnowledgeDocument> documentMap = new HashMap<>();
        for (KnowledgeDocument document : knowledgeDocumentRepository.findAll()) {
            documentMap.put(document.getId(), document);
        }

        List<Evidence> ranked = new ArrayList<>();
        for (DocumentChunk chunk : documentChunkRepository.findAll()) {
            KnowledgeDocument document = documentMap.get(chunk.getDocumentId());
            String title = document == null ? "Internal chunk" : document.getOriginalFilename();
            double score = scoreChunk(chunk.getContent(), queries);
            if (score <= 0) {
                continue;
            }
            score += titleBoost(title, queries);
            ranked.add(new Evidence(
                    UUID.randomUUID().toString(),
                    title,
                    summarize(chunk.getContent()),
                    null,
                    EvidenceSourceType.INTERNAL,
                    chunk.getDocumentId(),
                    chunk.getId(),
                    score
            ));
        }

        ranked.sort(Comparator.comparingDouble(Evidence::getScore).reversed());
        List<Evidence> result = ranked.stream().limit(maxResults).toList();

        evidenceRecordRepository.deleteByTaskIdAndSourceType(taskId, EvidenceSourceType.INTERNAL);
        List<EvidenceRecord> records = new ArrayList<>();
        for (Evidence evidence : result) {
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
            records.add(record);
        }
        evidenceRecordRepository.saveAll(records);
        return result;
    }

    private double scoreChunk(String content, List<String> queries) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        String lowered = content.toLowerCase(Locale.ROOT);
        double score = 0;
        for (String query : queries) {
            if (query == null || query.isBlank()) {
                continue;
            }
            Set<String> matchedTerms = new HashSet<>();
            for (String token : expandTerms(query)) {
                if (token.isBlank()) {
                    continue;
                }
                int occurrences = countOccurrences(lowered, token);
                if (occurrences > 0) {
                    matchedTerms.add(token);
                    score += 1.15D + Math.log1p(occurrences);
                }
            }
            score += matchedTerms.size() * 0.35D;
        }
        return score;
    }

    private double titleBoost(String title, List<String> queries) {
        if (title == null || title.isBlank()) {
            return 0D;
        }
        String lowered = title.toLowerCase(Locale.ROOT);
        double boost = 0D;
        for (String query : queries) {
            if (query == null || query.isBlank()) {
                continue;
            }
            for (String token : expandTerms(query)) {
                if (!token.isBlank() && lowered.contains(token)) {
                    boost += containsChinese(token) ? 0.45D : 0.25D;
                }
            }
        }
        return boost;
    }

    private List<String> expandTerms(String query) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            terms.add(token);
            if (containsChinese(token) && token.length() > 1) {
                for (int index = 0; index < token.length() - 1; index++) {
                    terms.add(token.substring(index, index + 2));
                }
            }
        }
        return new ArrayList<>(terms);
    }

    private int countOccurrences(String content, String term) {
        int occurrences = 0;
        int start = 0;
        while (start >= 0 && start < content.length()) {
            int found = content.indexOf(term, start);
            if (found < 0) {
                break;
            }
            occurrences++;
            start = found + Math.max(1, term.length());
        }
        return occurrences;
    }

    private boolean containsChinese(String token) {
        return token.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private String summarize(String content) {
        String collapsed = content.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 260 ? collapsed : collapsed.substring(0, 260) + "...";
    }
}
