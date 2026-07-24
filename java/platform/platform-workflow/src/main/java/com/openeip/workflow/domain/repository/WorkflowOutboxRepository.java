package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.entity.WorkflowOutbox;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowOutboxRepository extends JpaRepository<WorkflowOutbox, String> {
  List<WorkflowOutbox> findTop100ByStatusOrderByCreatedAtAsc(String status);
}
