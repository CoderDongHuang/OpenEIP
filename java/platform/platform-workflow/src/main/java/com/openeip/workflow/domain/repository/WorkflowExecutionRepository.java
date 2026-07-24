package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.ExecutionStatus;
import com.openeip.workflow.domain.entity.WorkflowExecution;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, String> {
  Page<WorkflowExecution> findAllByTenantIdAndWorkflowId(
      String tenantId, String workflowId, Pageable pageable);

  Optional<WorkflowExecution> findByIdAndTenantId(String id, String tenantId);

  Optional<WorkflowExecution> findByTenantIdAndWorkflowIdAndIdempotencyKey(
      String tenantId, String workflowId, String idempotencyKey);

  List<WorkflowExecution> findTop50ByStatusInAndResumeAtBeforeOrderByUpdatedAtAsc(
      List<ExecutionStatus> statuses, Instant now);

  List<WorkflowExecution> findTop50ByStatusInOrderByUpdatedAtAsc(List<ExecutionStatus> statuses);
}
