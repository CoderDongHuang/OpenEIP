package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.entity.WorkflowApproval;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowApprovalRepository extends JpaRepository<WorkflowApproval, String> {
  Optional<WorkflowApproval> findByIdAndTenantId(String id, String tenantId);

  Optional<WorkflowApproval> findByExecutionIdAndNodeId(String executionId, String nodeId);
}
