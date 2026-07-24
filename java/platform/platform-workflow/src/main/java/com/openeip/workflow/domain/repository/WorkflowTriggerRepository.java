package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.entity.WorkflowTrigger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTriggerRepository extends JpaRepository<WorkflowTrigger, String> {
  List<WorkflowTrigger> findAllByTenantIdAndWorkflowIdOrderByCreatedAtDesc(
      String tenantId, String workflowId);

  Optional<WorkflowTrigger> findByIdAndEnabledTrue(String id);

  List<WorkflowTrigger> findTop50ByTypeAndEnabledTrueAndNextFireAtBeforeOrderByNextFireAtAsc(
      String type, Instant now);

  List<WorkflowTrigger> findAllByTypeAndEnabledTrue(String type);
}
