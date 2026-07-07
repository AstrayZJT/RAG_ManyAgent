package com.astray.insightflow.report.api;

import com.astray.insightflow.retrieval.domain.EvidenceRecord;

public record ReportCitationResponse(
        String id,
        String title,
        String snippet,
        String url,
        String sourceType,
        String documentId,
        String chunkId,
        double score
) {

    public static ReportCitationResponse from(EvidenceRecord record) {
        return new ReportCitationResponse(
                record.getId(),
                record.getTitle(),
                record.getSnippet(),
                record.getUrl(),
                record.getSourceType() == null ? null : record.getSourceType().name(),
                record.getDocumentId(),
                record.getChunkId(),
                record.getScore()
        );
    }
}
