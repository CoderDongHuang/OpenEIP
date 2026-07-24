package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.entity.WorkflowNodeExecution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowNodeExecutionRepository
    extends JpaRepository<WorkflowNodeExecution, String> {
  List<WorkflowNodeExecution> findAllByExecutionIdOrderByCreatedAtAsc(String executionId);

  Optional<WorkflowNodeExecution> findFirstByExecutionIdAndNodeIdOrderByAttemptDesc(
      String executionId, String nodeId);
}
