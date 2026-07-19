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
        String documentHash,
        String chunkHash,
        Integer chunkIndex,
        Integer startOffset,
        Integer endOffset,
        double score,
        Double lexicalScore,
        Double vectorScore,
        Double titleBoost,
        Double rerankScore,
        String retrievalStrategy
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
                record.getDocumentHash(),
                record.getChunkHash(),
                record.getChunkIndex(),
                record.getStartOffset(),
                record.getEndOffset(),
                record.getScore(),
                record.getLexicalScore(),
                record.getVectorScore(),
                record.getTitleBoost(),
                record.getRerankScore(),
                record.getRetrievalStrategy()
        );
    }
}
