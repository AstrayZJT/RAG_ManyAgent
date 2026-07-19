package com.astray.insightflow.knowledge.persistence;

import com.astray.insightflow.knowledge.domain.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {

    List<KnowledgeDocument> findAllByOrderByUploadedAtDesc();

    Optional<KnowledgeDocument> findFirstByCollectionNameAndContentHash(String collectionName, String contentHash);
}
