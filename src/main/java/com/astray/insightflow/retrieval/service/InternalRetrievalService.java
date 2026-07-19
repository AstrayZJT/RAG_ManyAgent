package com.astray.insightflow.retrieval.service;

import com.astray.insightflow.config.AgentProperties;
import com.astray.insightflow.config.RagProperties;
import com.astray.insightflow.knowledge.domain.DocumentChunk;
import com.astray.insightflow.knowledge.domain.KnowledgeDocument;
import com.astray.insightflow.knowledge.persistence.DocumentChunkRepository;
import com.astray.insightflow.knowledge.persistence.KnowledgeDocumentRepository;
import com.astray.insightflow.retrieval.domain.EvidenceRecord;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.model.EvidenceScoreBreakdown;
import com.astray.insightflow.retrieval.model.RetrievalMode;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.retrieval.rerank.RerankService;
import com.astray.insightflow.retrieval.vector.VectorSearchMatch;
import com.astray.insightflow.retrieval.vector.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class InternalRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(InternalRetrievalService.class);

    private final DocumentChunkRepository documentChunkRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final VectorSearchService vectorSearchService;
    private final RerankService rerankService;
    private final RagProperties.Retrieval retrievalProperties;
    private final int maxResults;

    public InternalRetrievalService(DocumentChunkRepository documentChunkRepository,
                                    KnowledgeDocumentRepository knowledgeDocumentRepository,
                                    EvidenceRecordRepository evidenceRecordRepository,
                                    VectorSearchService vectorSearchService,
                                    RerankService rerankService,
                                    AgentProperties agentProperties,
                                    RagProperties ragProperties) {
        this.documentChunkRepository = documentChunkRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.vectorSearchService = vectorSearchService;
        this.rerankService = rerankService;
        this.retrievalProperties = ragProperties.retrieval();
        this.maxResults = agentProperties.search().maxResults();
    }

    @Transactional
    public List<Evidence> search(String taskId, List<String> queries) {
        List<Evidence> result = rank(queries);
        evidenceRecordRepository.deleteByTaskIdAndSourceType(taskId, EvidenceSourceType.INTERNAL);
        evidenceRecordRepository.saveAll(result.stream()
                .map(evidence -> toRecord(taskId, evidence))
                .toList());
        return result;
    }

    public List<Evidence> rank(List<String> queries) {
        return rank(queries, RetrievalMode.HYBRID);
    }

    public List<Evidence> rank(List<String> queries, RetrievalMode mode) {
        return rank(queries, mode, null);
    }

    public List<Evidence> rank(List<String> queries, RetrievalMode mode, String collectionName) {
        List<String> normalizedQueries = normalizeQueries(queries);
        if (normalizedQueries.isEmpty()) {
            return List.of();
        }

        Map<String, KnowledgeDocument> documentMap = new HashMap<>();
        for (KnowledgeDocument document : knowledgeDocumentRepository.findAll()) {
            if (StringUtils.hasText(collectionName) && !collectionName.equals(document.getCollectionName())) {
                continue;
            }
            documentMap.put(document.getId(), document);
        }

        Map<String, DocumentChunk> chunkMap = new LinkedHashMap<>();
        List<LexicalCandidate> lexicalCandidates = new ArrayList<>();
        for (DocumentChunk chunk : documentChunkRepository.findAll()) {
            KnowledgeDocument document = documentMap.get(chunk.getDocumentId());
            if (document == null && StringUtils.hasText(collectionName)) {
                continue;
            }
            chunkMap.put(chunk.getId(), chunk);
            String title = document == null ? "Internal chunk" : document.getOriginalFilename();
            LexicalScore lexicalScore = scoreChunk(chunk.getContent(), title, normalizedQueries);
            if (lexicalScore.total() > 0D) {
                lexicalCandidates.add(new LexicalCandidate(chunk.getId(), lexicalScore));
            }
        }
        lexicalCandidates.sort(Comparator.comparingDouble((LexicalCandidate candidate) -> candidate.score().total()).reversed());

        int candidateLimit = Math.max(maxResults, maxResults * retrievalProperties.candidateMultiplier());
        if (mode == RetrievalMode.HYBRID_RERANK && rerankService.isEnabled()) {
            candidateLimit = Math.max(candidateLimit, rerankService.candidateCount());
        }
        List<LexicalCandidate> lexicalPool = lexicalCandidates.stream().limit(candidateLimit).toList();
        List<VectorSearchMatch> vectorMatches = vectorMatches(normalizedQueries, candidateLimit, mode, collectionName);

        Map<String, Integer> lexicalRanks = ranks(lexicalPool.stream().map(LexicalCandidate::chunkId).toList());
        Map<String, LexicalScore> lexicalScores = new HashMap<>();
        lexicalPool.forEach(candidate -> lexicalScores.put(candidate.chunkId(), candidate.score()));
        Map<String, Integer> vectorRanks = ranks(vectorMatches.stream().map(VectorSearchMatch::chunkId).toList());
        Map<String, Double> vectorScores = new HashMap<>();
        vectorMatches.forEach(match -> vectorScores.put(match.chunkId(), match.score()));

        Set<String> candidateIds = new LinkedHashSet<>();
        candidateIds.addAll(lexicalRanks.keySet());
        candidateIds.addAll(vectorRanks.keySet());

        boolean lexicalAvailable = !lexicalRanks.isEmpty();
        boolean vectorAvailable = !vectorRanks.isEmpty();
        String strategy = vectorAvailable ? "weighted_rrf_hybrid" : "keyword_rrf";
        List<Evidence> ranked = new ArrayList<>();
        for (String chunkId : candidateIds) {
            DocumentChunk chunk = chunkMap.get(chunkId);
            if (chunk == null) {
                continue;
            }
            KnowledgeDocument document = documentMap.get(chunk.getDocumentId());
            LexicalScore lexical = lexicalScores.getOrDefault(chunkId, LexicalScore.empty());
            Integer lexicalRank = lexicalRanks.get(chunkId);
            Integer vectorRank = vectorRanks.get(chunkId);
            Double vectorScore = vectorScores.get(chunkId);
            double fusedScore = fusedScore(lexicalRank, vectorRank, lexicalAvailable, vectorAvailable);
            ranked.add(toEvidence(
                    chunk,
                    document,
                    lexical,
                    vectorScore,
                    lexicalRank,
                    vectorRank,
                    fusedScore,
                    strategy
            ));
        }

        ranked.sort(Comparator.comparingDouble(Evidence::getScore).reversed()
                .thenComparing(evidence -> evidence.getChunkId() == null ? "" : evidence.getChunkId()));
        if (mode == RetrievalMode.HYBRID_RERANK && rerankService.isEnabled()) {
            try {
                return rerankService.rerank(
                                normalizedQueries,
                                ranked.stream().limit(candidateLimit).toList()
                        ).stream()
                        .limit(maxResults)
                        .toList();
            } catch (RuntimeException exception) {
                log.warn("Rerank failed; falling back to hybrid ranking: {}", exception.getMessage());
            }
        }
        return ranked.stream().limit(maxResults).toList();
    }

    private List<VectorSearchMatch> vectorMatches(List<String> queries,
                                                  int candidateLimit,
                                                  RetrievalMode mode,
                                                  String collectionName) {
        if (mode == RetrievalMode.KEYWORD || !vectorSearchService.isEnabled()) {
            return List.of();
        }
        try {
            return vectorSearchService.search(queries, candidateLimit, collectionName);
        } catch (RuntimeException exception) {
            log.warn("Vector retrieval failed; falling back to lexical retrieval: {}", exception.getMessage());
            return List.of();
        }
    }

    private LexicalScore scoreChunk(String content, String title, List<String> queries) {
        if (!StringUtils.hasText(content)) {
            return LexicalScore.empty();
        }
        String loweredContent = content.toLowerCase(Locale.ROOT);
        double termFrequencyScore = 0D;
        double coverageScore = 0D;
        for (String query : queries) {
            Map<String, Double> weightedTerms = expandTerms(query);
            double totalTermWeight = weightedTerms.values().stream().mapToDouble(Double::doubleValue).sum();
            double matchedTermWeight = 0D;
            for (Map.Entry<String, Double> term : weightedTerms.entrySet()) {
                int occurrences = countOccurrences(loweredContent, term.getKey());
                if (occurrences > 0) {
                    matchedTermWeight += term.getValue();
                    termFrequencyScore += term.getValue() * (1.15D + Math.log1p(occurrences));
                }
            }
            if (totalTermWeight > 0D) {
                coverageScore += matchedTermWeight / totalTermWeight;
            }
        }
        coverageScore *= 1.5D;
        double titleBoost = titleBoost(title, queries);
        return new LexicalScore(termFrequencyScore, coverageScore, titleBoost);
    }

    private double titleBoost(String title, List<String> queries) {
        if (!StringUtils.hasText(title)) {
            return 0D;
        }
        String lowered = title.toLowerCase(Locale.ROOT);
        double boost = 0D;
        for (String query : queries) {
            for (Map.Entry<String, Double> term : expandTerms(query).entrySet()) {
                if (lowered.contains(term.getKey())) {
                    boost += term.getValue() * (containsChinese(term.getKey()) ? 0.45D : 0.25D);
                }
            }
        }
        return boost;
    }

    private Map<String, Double> expandTerms(String query) {
        Map<String, Double> terms = new LinkedHashMap<>();
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        for (String token : normalized.split("[\\s\\p{Punct}\\p{IsPunctuation}]+")) {
            if (token.isBlank()) {
                continue;
            }
            terms.merge(token, 1D, Math::max);
            if (containsChinese(token) && token.length() >= 4) {
                addNgrams(terms, token, 3, 0.55D);
                addNgrams(terms, token, 2, 0.25D);
            }
        }
        return terms;
    }

    private void addNgrams(Map<String, Double> terms, String token, int size, double weight) {
        if (token.length() < size) {
            return;
        }
        for (int index = 0; index <= token.length() - size; index++) {
            terms.merge(token.substring(index, index + size), weight, Math::max);
        }
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

    private double fusedScore(Integer lexicalRank,
                              Integer vectorRank,
                              boolean lexicalAvailable,
                              boolean vectorAvailable) {
        double keywordWeight = lexicalAvailable ? retrievalProperties.keywordWeight() : 0D;
        double vectorWeight = vectorAvailable ? retrievalProperties.vectorWeight() : 0D;
        double denominator = (keywordWeight + vectorWeight) / (retrievalProperties.rrfK() + 1D);
        if (denominator <= 0D) {
            return 0D;
        }
        double score = 0D;
        if (lexicalRank != null) {
            score += keywordWeight / (retrievalProperties.rrfK() + lexicalRank);
        }
        if (vectorRank != null) {
            score += vectorWeight / (retrievalProperties.rrfK() + vectorRank);
        }
        return Math.min(1D, score / denominator);
    }

    private Evidence toEvidence(DocumentChunk chunk,
                                KnowledgeDocument document,
                                LexicalScore lexical,
                                Double vectorScore,
                                Integer lexicalRank,
                                Integer vectorRank,
                                double fusedScore,
                                String strategy) {
        String title = document == null ? "Internal chunk" : document.getOriginalFilename();
        Evidence evidence = new Evidence(
                UUID.randomUUID().toString(),
                title,
                summarize(chunk.getContent()),
                null,
                EvidenceSourceType.INTERNAL,
                chunk.getDocumentId(),
                chunk.getId(),
                fusedScore
        );
        evidence.setDocumentHash(document == null ? null : document.getContentHash());
        evidence.setChunkHash(chunk.getContentHash());
        evidence.setChunkIndex(chunk.getChunkIndex());
        evidence.setStartOffset(chunk.getStartOffset());
        evidence.setEndOffset(chunk.getEndOffset());
        evidence.setScoreBreakdown(new EvidenceScoreBreakdown(
                lexical.total(),
                lexical.termFrequencyScore(),
                lexical.coverageScore(),
                lexical.titleBoost(),
                vectorScore,
                lexicalRank,
                vectorRank,
                fusedScore,
                null,
                strategy
        ));
        return evidence;
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
        record.setDocumentHash(evidence.getDocumentHash());
        record.setChunkHash(evidence.getChunkHash());
        record.setChunkIndex(evidence.getChunkIndex());
        record.setStartOffset(evidence.getStartOffset());
        record.setEndOffset(evidence.getEndOffset());
        record.setScore(evidence.getScore());
        if (evidence.getScoreBreakdown() != null) {
            record.setLexicalScore(evidence.getScoreBreakdown().lexicalScore());
            record.setVectorScore(evidence.getScoreBreakdown().vectorScore());
            record.setTitleBoost(evidence.getScoreBreakdown().titleBoost());
            record.setRerankScore(evidence.getScoreBreakdown().rerankScore());
            record.setRetrievalStrategy(evidence.getScoreBreakdown().strategy());
        }
        record.setCreatedAt(Instant.now());
        return record;
    }

    private Map<String, Integer> ranks(List<String> ids) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            ranks.putIfAbsent(ids.get(index), index + 1);
        }
        return ranks;
    }

    private List<String> normalizeQueries(List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String query : queries) {
            if (StringUtils.hasText(query)) {
                normalized.add(query.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    private String summarize(String content) {
        String collapsed = content.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 260 ? collapsed : collapsed.substring(0, 260) + "...";
    }

    private record LexicalCandidate(String chunkId, LexicalScore score) {
    }

    private record LexicalScore(double termFrequencyScore, double coverageScore, double titleBoost) {

        private static LexicalScore empty() {
            return new LexicalScore(0D, 0D, 0D);
        }

        private double total() {
            return termFrequencyScore + coverageScore + titleBoost;
        }
    }
}
