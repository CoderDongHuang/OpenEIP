package com.openeip.knowledge.domain.repository;

import com.openeip.knowledge.domain.entity.ProcessedKnowledgeEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedKnowledgeEventRepository
    extends JpaRepository<ProcessedKnowledgeEvent, String> {
  Optional<ProcessedKnowledgeEvent> findByTenantIdAndEventId(String tenantId, String eventId);
}
