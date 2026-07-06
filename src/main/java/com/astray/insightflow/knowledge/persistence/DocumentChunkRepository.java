package com.astray.insightflow.knowledge.persistence;

import com.astray.insightflow.knowledge.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, String> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    void deleteByDocumentId(String documentId);
}
