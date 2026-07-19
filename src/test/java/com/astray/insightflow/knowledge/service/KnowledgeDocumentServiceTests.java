package com.astray.insightflow.knowledge.service;

import com.astray.insightflow.config.RagProperties;
import com.astray.insightflow.knowledge.persistence.DocumentChunkRepository;
import com.astray.insightflow.knowledge.persistence.KnowledgeDocumentRepository;
import com.astray.insightflow.retrieval.vector.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class KnowledgeDocumentServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void splitIntoChunksKeepsOffsetsAndSentenceOverlap() throws IOException {
        KnowledgeDocumentService service = new KnowledgeDocumentService(
                mock(KnowledgeDocumentRepository.class),
                mock(DocumentChunkRepository.class),
                ragProperties(),
                mock(VectorSearchService.class)
        );

        String content = """
                Alpha sentence one. Alpha sentence two continues with more words.

                Beta sentence three keeps enough text to force another chunk. Gamma sentence four closes the sample.
                """;

        List<KnowledgeDocumentService.ChunkSlice> chunks = service.splitIntoChunks(content, 72, 16);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().length() <= 72));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.startOffset() < chunk.endOffset()));
        assertTrue(chunks.get(1).startOffset() < chunks.get(0).endOffset());
        assertFalse(chunks.get(0).content().startsWith(" "));
        assertFalse(chunks.get(0).content().endsWith(" "));
    }

    private RagProperties ragProperties() {
        return new RagProperties(
                tempDir.toString(),
                new RagProperties.Pgvector(
                        "localhost",
                        5432,
                        "test",
                        "postgres",
                        "",
                        "knowledge_embeddings",
                        false,
                        100
                ),
                new RagProperties.Chunking(800, 120),
                RagProperties.Embedding.defaults(),
                RagProperties.Retrieval.defaults()
        );
    }
}
