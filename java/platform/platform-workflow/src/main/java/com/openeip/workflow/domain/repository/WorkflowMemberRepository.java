package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.entity.WorkflowMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowMemberRepository extends JpaRepository<WorkflowMember, String> {
  Optional<WorkflowMember> findByTenantIdAndWorkflowIdAndUserId(
      String tenantId, String workflowId, String userId);
}
