package com.openeip.workflow.infrastructure;

import com.openeip.workflow.application.WorkflowExecutionService;
import com.openeip.workflow.application.WorkflowTriggerService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "openeip.workflow",
    name = "scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowScheduler {
  private final WorkflowExecutionService executions;
  private final WorkflowTriggerService triggers;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are shared services.")
  public WorkflowScheduler(WorkflowExecutionService executions, WorkflowTriggerService triggers) {
    this.executions = executions;
    this.triggers = triggers;
  }

  @Scheduled(fixedDelayString = "${openeip.workflow.scheduler-delay-ms:1000}")
  public void tick() {
    executions.advanceDue();
    triggers.fireDue();
  }
}
