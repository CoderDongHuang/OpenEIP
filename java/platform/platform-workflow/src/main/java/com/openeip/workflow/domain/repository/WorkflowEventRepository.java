package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.entity.WorkflowEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, String> {
  List<WorkflowEvent> findAllByExecutionIdAndSequenceGreaterThanOrderBySequenceAsc(
      String executionId, long sequence);
}
