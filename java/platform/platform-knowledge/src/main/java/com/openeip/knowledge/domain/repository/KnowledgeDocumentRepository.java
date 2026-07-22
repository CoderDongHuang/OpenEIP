package com.openeip.knowledge.domain.repository;

import com.openeip.knowledge.domain.entity.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {
  Optional<KnowledgeDocument> findByTenantIdAndKnowledgeBaseIdAndDocumentId(
      String tenantId, String knowledgeBaseId, String documentId);

  List<KnowledgeDocument> findAllByTenantIdAndKnowledgeBaseIdOrderByCreatedAtDesc(
      String tenantId, String knowledgeBaseId);

  List<KnowledgeDocument> findAllByTenantIdAndDocumentId(String tenantId, String documentId);
}
