package com.openeip.workflow.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.domain.entity.WorkflowEvent;
import com.openeip.workflow.domain.entity.WorkflowExecution;
import com.openeip.workflow.domain.repository.WorkflowEventRepository;
import com.openeip.workflow.domain.repository.WorkflowOutboxRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class WorkflowEventServiceTest {
  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void publishesImmediatelyWithoutTransactionAndAfterCommitWithSynchronization() {
    WorkflowEventRepository events = mock(WorkflowEventRepository.class);
    WorkflowOutboxRepository outbox = mock(WorkflowOutboxRepository.class);
    WorkflowEventStream stream = mock(WorkflowEventStream.class);
    when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    WorkflowEventService service =
        new WorkflowEventService(events, outbox, stream, new ObjectMapper());

    WorkflowEvent immediate =
        service.append(execution(), "workflow.execution.started", null, Map.of());
    verify(stream).publish(immediate);

    TransactionSynchronizationManager.initSynchronization();
    WorkflowEvent committed =
        service.append(execution(), "workflow.execution.completed", null, Map.of());
    verify(stream, never()).publish(committed);
    TransactionSynchronizationManager.getSynchronizations().forEach(value -> value.afterCommit());
    verify(stream).publish(committed);
  }

  private static WorkflowExecution execution() {
    return new WorkflowExecution(
        UUID.randomUUID().toString(),
        "default",
        UUID.randomUUID().toString(),
        1,
        "MANUAL",
        UUID.randomUUID().toString(),
        "{}",
        Instant.parse("2026-07-24T00:00:00Z"));
  }
}
