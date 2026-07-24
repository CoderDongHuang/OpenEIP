package com.openeip.workflow.infrastructure;

import com.openeip.workflow.domain.repository.WorkflowOutboxRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "openeip.workflow", name = "kafka-enabled", havingValue = "true")
public class WorkflowOutboxPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowOutboxPublisher.class);
  private final WorkflowOutboxRepository outbox;
  private final KafkaTemplate<String, String> kafka;
  private final String topic;
  private final Clock clock = Clock.systemUTC();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are shared services.")
  public WorkflowOutboxPublisher(
      WorkflowOutboxRepository outbox,
      KafkaTemplate<String, String> kafka,
      @Value("${openeip.workflow.event-topic:workflow.events.v1}") String topic) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.topic = topic;
  }

  @Scheduled(fixedDelayString = "${openeip.workflow.outbox-delay-ms:1000}")
  @Transactional
  public void publish() {
    for (var event : outbox.findTop100ByStatusOrderByCreatedAtAsc("PENDING")) {
      try {
        kafka.send(topic, event.getEventId(), event.getPayloadJson()).get(5, TimeUnit.SECONDS);
        event.delivered(clock.instant());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        LOGGER.warn("Workflow outbox delivery interrupted eventId={}", event.getEventId());
        return;
      } catch (ExecutionException | TimeoutException exception) {
        LOGGER.warn("Workflow outbox delivery deferred eventId={}", event.getEventId());
        return;
      }
    }
  }
}
