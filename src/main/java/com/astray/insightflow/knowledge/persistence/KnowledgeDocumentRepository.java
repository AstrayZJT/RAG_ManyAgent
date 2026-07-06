package com.astray.insightflow.knowledge.persistence;

import com.astray.insightflow.knowledge.domain.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {
}
