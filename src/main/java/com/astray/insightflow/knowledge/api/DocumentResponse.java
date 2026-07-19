package com.astray.insightflow.knowledge.api;

import com.astray.insightflow.knowledge.domain.KnowledgeDocument;

import java.time.Instant;

public record DocumentResponse(
        String id,
        String originalFilename,
        String mediaType,
        String status,
        String storagePath,
        String contentHash,
        String collectionName,
        Instant uploadedAt,
        Instant indexedAt,
        String errorMessage
) {

    public static DocumentResponse from(KnowledgeDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getMediaType(),
                document.getStatus().name(),
                document.getStoragePath(),
                document.getContentHash(),
                document.getCollectionName(),
                document.getUploadedAt(),
                document.getIndexedAt(),
                document.getErrorMessage()
        );
    }
}
