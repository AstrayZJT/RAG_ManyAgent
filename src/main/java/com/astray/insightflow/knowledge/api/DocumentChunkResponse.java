package com.astray.insightflow.knowledge.api;

import com.astray.insightflow.knowledge.domain.DocumentChunk;

import java.time.Instant;

public record DocumentChunkResponse(
        String id,
        String documentId,
        int chunkIndex,
        Integer startOffset,
        Integer endOffset,
        String contentHash,
        int tokenCount,
        String preview,
        Instant createdAt
) {

    private static final int PREVIEW_MAX_LENGTH = 260;

    public static DocumentChunkResponse from(DocumentChunk chunk) {
        return new DocumentChunkResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getStartOffset(),
                chunk.getEndOffset(),
                chunk.getContentHash(),
                chunk.getTokenCount(),
                preview(chunk.getContent()),
                chunk.getCreatedAt()
        );
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        String collapsed = content.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= PREVIEW_MAX_LENGTH
                ? collapsed
                : collapsed.substring(0, PREVIEW_MAX_LENGTH) + "...";
    }
}
