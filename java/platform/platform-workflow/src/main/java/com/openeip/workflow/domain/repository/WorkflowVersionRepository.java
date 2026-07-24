package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.entity.WorkflowVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, String> {
  List<WorkflowVersion> findAllByTenantIdAndWorkflowIdOrderByVersionDesc(
      String tenantId, String workflowId);

  Optional<WorkflowVersion> findByTenantIdAndWorkflowIdAndVersion(
      String tenantId, String workflowId, int version);
}
