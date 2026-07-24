package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.entity.WorkflowApprovalDecision;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowApprovalDecisionRepository
    extends JpaRepository<WorkflowApprovalDecision, String> {
  Optional<WorkflowApprovalDecision> findByTenantIdAndApprovalIdAndIdempotencyKey(
      String tenantId, String approvalId, String idempotencyKey);

  boolean existsByTenantIdAndApprovalIdAndAssigneeId(
      String tenantId, String approvalId, String assigneeId);

  long countByTenantIdAndApprovalIdAndDecision(String tenantId, String approvalId, String decision);

  void deleteAllByTenantIdAndApprovalId(String tenantId, String approvalId);
}
