package com.openeip.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.application.WorkflowEventStream;
import com.openeip.workflow.application.WorkflowExecutionService;
import com.openeip.workflow.application.WorkflowTriggerService;
import com.openeip.workflow.domain.entity.WorkflowEvent;
import com.openeip.workflow.domain.entity.WorkflowOutbox;
import com.openeip.workflow.domain.repository.WorkflowOutboxRepository;
import com.openeip.workflow.shared.WorkflowException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class WorkflowInfrastructureTest {
  private static final String EXTERNAL_TENANT = "11111111-1111-4111-8111-111111111111";

  @Test
  void schedulerDelegatesRecoveryAndCronWork() {
    WorkflowExecutionService executions = mock(WorkflowExecutionService.class);
    WorkflowTriggerService triggers = mock(WorkflowTriggerService.class);
    new WorkflowScheduler(executions, triggers).tick();
    verify(executions).advanceDue();
    verify(triggers).fireDue();
  }

  @Test
  void kafkaListenerValidatesEnvelopeAndRoutesFingerprint() {
    WorkflowTriggerService triggers = mock(WorkflowTriggerService.class);
    WorkflowKafkaEventListener listener =
        new WorkflowKafkaEventListener(
            triggers, new ObjectMapper().findAndRegisterModules(), EXTERNAL_TENANT);
    String eventId = UUID.randomUUID().toString();
    listener.receive(
        "{\"eventId\":\""
            + eventId
            + "\",\"eventType\":\"order.created\",\"eventVersion\":1,"
            + "\"timestamp\":\"2026-07-24T00:00:00Z\",\"source\":\"test\","
            + "\"tenantId\":\""
            + EXTERNAL_TENANT
            + "\",\"payload\":{\"orderId\":\"42\"}}");
    verify(triggers)
        .processEvent(
            org.mockito.ArgumentMatchers.eq(eventId),
            org.mockito.ArgumentMatchers.eq("order.created"),
            anyString(),
            org.mockito.ArgumentMatchers.any());

    assertThatThrownBy(() -> listener.receive("{}")).isInstanceOf(WorkflowException.class);
    assertThatThrownBy(
            () ->
                listener.receive(
                    "{\"eventId\":\"bad\",\"eventType\":\"order.created\","
                        + "\"eventVersion\":1,\"timestamp\":\"2026-07-24T00:00:00Z\","
                        + "\"source\":\"test\",\"tenantId\":\""
                        + EXTERNAL_TENANT
                        + "\",\"payload\":{}}"))
        .isInstanceOf(WorkflowException.class);
    assertThatThrownBy(() -> listener.receive("x".repeat(70_000)))
        .isInstanceOf(WorkflowException.class);
    assertThatThrownBy(
            () -> new WorkflowKafkaEventListener(triggers, new ObjectMapper(), "bad-tenant"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void outboxPublisherMarksAcknowledgedRowsAndDefersFailures() throws Exception {
    WorkflowOutboxRepository repository = mock(WorkflowOutboxRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    WorkflowOutbox event = outbox();
    when(repository.findTop100ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(event));
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    new WorkflowOutboxPublisher(repository, kafka, "workflow.events.v1").publish();
    verify(kafka).send("workflow.events.v1", event.getEventId(), event.getPayloadJson());

    WorkflowOutbox failed = outbox();
    when(repository.findTop100ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(failed));
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
    new WorkflowOutboxPublisher(repository, kafka, "workflow.events.v1").publish();
  }

  @Test
  void eventStreamQueuesBacklogAndPublishesLiveEvents() {
    ObjectMapper mapper = new ObjectMapper();
    WorkflowEventStream stream = new WorkflowEventStream(mapper);
    WorkflowEvent event =
        new WorkflowEvent(
            UUID.randomUUID().toString(),
            "default",
            UUID.randomUUID().toString(),
            1,
            "workflow.execution.started",
            null,
            "{\"attempt\":1}",
            Instant.parse("2026-07-24T00:00:00Z"));
    var payload = WorkflowEventStream.StreamEvent.from(event, mapper);
    assertThat(payload.data().path("attempt").asInt()).isEqualTo(1);
    assertThat(payload.type()).isEqualTo("workflow.execution.started");
    var emitter = stream.open(event.getExecutionId(), List.of(event));
    stream.publish(event);
    emitter.complete();
  }

  @Test
  void dlqRecoveryPublishesOnlySanitizedMetadata() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    ConsumerRecord<String, String> record =
        new ConsumerRecord<>("workflow.commands.v1", 2, 9, "secret-key", "raw-secret-body");

    WorkflowKafkaConfiguration.recover(kafka, new ObjectMapper(), "workflow.events.dlq.v1", record);

    verify(kafka)
        .send(
            org.mockito.ArgumentMatchers.eq("workflow.events.dlq.v1"),
            anyString(),
            argThat(
                value ->
                    value.contains("\"offset\":9")
                        && !value.contains("raw-secret-body")
                        && !value.contains("secret-key")));
  }

  private static WorkflowOutbox outbox() {
    return new WorkflowOutbox(
        UUID.randomUUID().toString(),
        "default",
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "workflow.execution.started",
        "{}",
        Instant.parse("2026-07-24T00:00:00Z"));
  }
}
