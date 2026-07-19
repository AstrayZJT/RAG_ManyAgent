package com.astray.insightflow.retrieval.service;

import com.astray.insightflow.config.AgentProperties;
import com.astray.insightflow.config.RagProperties;
import com.astray.insightflow.knowledge.domain.DocumentChunk;
import com.astray.insightflow.knowledge.domain.KnowledgeDocument;
import com.astray.insightflow.knowledge.persistence.DocumentChunkRepository;
import com.astray.insightflow.knowledge.persistence.KnowledgeDocumentRepository;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.retrieval.rerank.RerankService;
import com.astray.insightflow.retrieval.vector.VectorSearchMatch;
import com.astray.insightflow.retrieval.vector.VectorSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalRetrievalServiceTests {

    @Test
    void hybridRankingKeepsVectorOnlyCandidatesAndScoreBreakdown() {
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        EvidenceRecordRepository evidenceRepository = mock(EvidenceRecordRepository.class);
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);

        KnowledgeDocument document = document("doc-1", "market.txt", "document-hash");
        DocumentChunk keywordChunk = chunk("chunk-1", "doc-1", 0,
                "比亚迪依靠电池、电机和电控一体化供应链形成成本优势。", "chunk-hash-1");
        DocumentChunk semanticChunk = chunk("chunk-2", "doc-1", 1,
                "理想汽车围绕家庭用户打造增程 SUV 和高配置座舱。", "chunk-hash-2");

        when(documentRepository.findAll()).thenReturn(List.of(document));
        when(chunkRepository.findAll()).thenReturn(List.of(keywordChunk, semanticChunk));
        when(vectorSearchService.isEnabled()).thenReturn(true);
        when(vectorSearchService.search(anyList(), anyInt(), isNull())).thenReturn(List.of(
                new VectorSearchMatch("chunk-2", 0.93D),
                new VectorSearchMatch("chunk-1", 0.86D)
        ));

        List<Evidence> result = service(chunkRepository, documentRepository, evidenceRepository, vectorSearchService)
                .rank(List.of("比亚迪供应链成本优势"));

        assertEquals(2, result.size());
        assertEquals("chunk-1", result.getFirst().getChunkId());
        assertTrue(result.stream().anyMatch(evidence -> evidence.getChunkId().equals("chunk-2")));
        assertEquals("document-hash", result.getFirst().getDocumentHash());
        assertEquals("chunk-hash-1", result.getFirst().getChunkHash());
        assertEquals(0, result.getFirst().getChunkIndex());
        assertEquals(0, result.getFirst().getStartOffset());
        assertNotNull(result.getFirst().getScoreBreakdown());
        assertEquals("weighted_rrf_hybrid", result.getFirst().getScoreBreakdown().strategy());

        Evidence vectorOnly = result.stream()
                .filter(evidence -> evidence.getChunkId().equals("chunk-2"))
                .findFirst()
                .orElseThrow();
        assertEquals(0D, vectorOnly.getScoreBreakdown().lexicalScore());
        assertEquals(0.93D, vectorOnly.getScoreBreakdown().vectorScore());
    }

    @Test
    void vectorFailureFallsBackToKeywordRanking() {
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        EvidenceRecordRepository evidenceRepository = mock(EvidenceRecordRepository.class);
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);

        KnowledgeDocument document = document("doc-1", "market.txt", "document-hash");
        DocumentChunk chunk = chunk("chunk-1", "doc-1", 0,
                "特斯拉依靠品牌和自动驾驶能力维持高端市场认知。", "chunk-hash-1");
        when(documentRepository.findAll()).thenReturn(List.of(document));
        when(chunkRepository.findAll()).thenReturn(List.of(chunk));
        when(vectorSearchService.isEnabled()).thenReturn(true);
        when(vectorSearchService.search(anyList(), anyInt(), isNull())).thenThrow(new IllegalStateException("vector unavailable"));

        List<Evidence> result = service(chunkRepository, documentRepository, evidenceRepository, vectorSearchService)
                .rank(List.of("特斯拉自动驾驶"));

        assertEquals(1, result.size());
        assertEquals("keyword_rrf", result.getFirst().getScoreBreakdown().strategy());
        assertEquals(1D, result.getFirst().getScore());
    }

    private InternalRetrievalService service(DocumentChunkRepository chunkRepository,
                                             KnowledgeDocumentRepository documentRepository,
                                             EvidenceRecordRepository evidenceRepository,
                                             VectorSearchService vectorSearchService) {
        AgentProperties agentProperties = new AgentProperties(
                new AgentProperties.Search(5, 4, 8, 15_000, "test"),
                new AgentProperties.Webpage(8_000, 240)
        );
        RagProperties ragProperties = new RagProperties(
                "knowledge",
                null,
                new RagProperties.Chunking(800, 120),
                RagProperties.Embedding.defaults(),
                new RagProperties.Retrieval(0.45D, 0.55D, 60, 4)
        );
        return new InternalRetrievalService(
                chunkRepository,
                documentRepository,
                evidenceRepository,
                vectorSearchService,
                mock(RerankService.class),
                agentProperties,
                ragProperties
        );
    }

    private KnowledgeDocument document(String id, String filename, String contentHash) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setOriginalFilename(filename);
        document.setContentHash(contentHash);
        return document;
    }

    private DocumentChunk chunk(String id,
                                String documentId,
                                int chunkIndex,
                                String content,
                                String contentHash) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setStartOffset(chunkIndex * 100);
        chunk.setEndOffset((chunkIndex * 100) + content.length());
        chunk.setContent(content);
        chunk.setContentHash(contentHash);
        return chunk;
    }
}
