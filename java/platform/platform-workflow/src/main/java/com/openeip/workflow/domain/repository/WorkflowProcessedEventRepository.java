package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.entity.WorkflowProcessedEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowProcessedEventRepository
    extends JpaRepository<WorkflowProcessedEvent, String> {
  Optional<WorkflowProcessedEvent> findByTenantIdAndEventId(String tenantId, String eventId);
}
