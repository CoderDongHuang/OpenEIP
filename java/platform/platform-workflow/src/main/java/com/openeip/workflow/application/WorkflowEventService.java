package com.openeip.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.domain.entity.WorkflowEvent;
import com.openeip.workflow.domain.entity.WorkflowExecution;
import com.openeip.workflow.domain.entity.WorkflowOutbox;
import com.openeip.workflow.domain.repository.WorkflowEventRepository;
import com.openeip.workflow.domain.repository.WorkflowOutboxRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class WorkflowEventService {
  private final WorkflowEventRepository events;
  private final WorkflowOutboxRepository outbox;
  private final WorkflowEventStream stream;
  private final ObjectMapper mapper;
  private final Clock clock = Clock.systemUTC();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are shared services.")
  public WorkflowEventService(
      WorkflowEventRepository events,
      WorkflowOutboxRepository outbox,
      WorkflowEventStream stream,
      ObjectMapper mapper) {
    this.events = events;
    this.outbox = outbox;
    this.stream = stream;
    this.mapper = mapper;
  }

  public WorkflowEvent append(
      WorkflowExecution execution, String type, String nodeId, Map<String, ?> data) {
    Instant now = clock.instant();
    String eventId = UUID.randomUUID().toString();
    String dataJson = json(data);
    WorkflowEvent event =
        events.save(
            new WorkflowEvent(
                eventId,
                execution.getTenantId(),
                execution.getId(),
                execution.nextSequence(),
                type,
                nodeId,
                dataJson,
                now));
    String payload =
        json(
            Map.of(
                "eventId",
                eventId,
                "eventType",
                type,
                "eventVersion",
                1,
                "tenantId",
                execution.getTenantId(),
                "executionId",
                execution.getId(),
                "sequence",
                event.getSequence(),
                "timestamp",
                now.toString(),
                "data",
                data));
    outbox.save(
        new WorkflowOutbox(
            UUID.randomUUID().toString(),
            execution.getTenantId(),
            eventId,
            execution.getId(),
            type,
            payload,
            now));
    publishAfterCommit(event);
    return event;
  }

  private void publishAfterCommit(WorkflowEvent event) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      stream.publish(event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            stream.publish(event);
          }
        });
  }

  private String json(Object value) {
    try {
      String result = mapper.writeValueAsString(value);
      if (result.length() > 16_384) {
        throw new IllegalArgumentException("Workflow event is too large");
      }
      return result;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Workflow event is invalid", exception);
    }
  }
}
