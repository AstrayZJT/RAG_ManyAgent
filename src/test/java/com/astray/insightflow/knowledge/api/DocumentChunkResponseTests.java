package com.astray.insightflow.knowledge.api;

import com.astray.insightflow.knowledge.domain.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkResponseTests {

    @Test
    void fromIncludesTraceFieldsAndCollapsesPreviewWhitespace() {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId("chunk-1");
        chunk.setDocumentId("doc-1");
        chunk.setChunkIndex(2);
        chunk.setStartOffset(128);
        chunk.setEndOffset(360);
        chunk.setContentHash("abc123");
        chunk.setTokenCount(232);
        chunk.setContent("First line.\n\nSecond line with detail.");
        chunk.setCreatedAt(Instant.parse("2026-07-18T00:00:00Z"));

        DocumentChunkResponse response = DocumentChunkResponse.from(chunk);

        assertEquals("chunk-1", response.id());
        assertEquals("doc-1", response.documentId());
        assertEquals(2, response.chunkIndex());
        assertEquals(128, response.startOffset());
        assertEquals(360, response.endOffset());
        assertEquals("abc123", response.contentHash());
        assertEquals(232, response.tokenCount());
        assertEquals("First line. Second line with detail.", response.preview());
        assertTrue(response.preview().length() <= 260);
    }
}
