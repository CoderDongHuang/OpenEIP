package com.openeip.workflow.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.application.WorkflowGraphValidator;
import com.openeip.workflow.application.WorkflowTriggerService;
import com.openeip.workflow.shared.WorkflowException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "openeip.workflow", name = "kafka-enabled", havingValue = "true")
public class WorkflowKafkaEventListener {
  private static final Pattern EVENT_TYPE =
      Pattern.compile("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+){1,7}$");
  private final WorkflowTriggerService triggers;
  private final ObjectMapper mapper;
  private final String externalTenantId;

  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
      justification = "Spring service is shared; ObjectMapper is defensively copied.")
  public WorkflowKafkaEventListener(
      WorkflowTriggerService triggers,
      ObjectMapper mapper,
      @Value("${openeip.workflow.external-tenant-id}") String externalTenantId) {
    UUID.fromString(externalTenantId);
    this.triggers = triggers;
    this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.externalTenantId = externalTenantId;
  }

  @KafkaListener(
      topics = "${openeip.workflow.inbound-topic}",
      groupId = "${openeip.workflow.kafka-group-id}",
      containerFactory = "workflowKafkaListenerContainerFactory")
  public void receive(String json) {
    if (json == null || json.getBytes(StandardCharsets.UTF_8).length > 64 * 1024) {
      throw WorkflowException.invalid("Invalid workflow event envelope");
    }
    try {
      EventEnvelope event = mapper.readValue(json, EventEnvelope.class);
      if (event.eventVersion() != 1
          || event.eventId() == null
          || !externalTenantId.equals(event.tenantId())
          || event.eventType() == null
          || event.eventType().length() > 128
          || !EVENT_TYPE.matcher(event.eventType()).matches()
          || event.timestamp() == null
          || event.source() == null
          || event.source().isBlank()
          || event.source().length() > 128
          || event.payload() == null
          || !event.payload().isObject()
          || event.payload().size() > 100) {
        throw WorkflowException.invalid("Invalid workflow event envelope");
      }
      UUID.fromString(event.eventId());
      String fingerprint =
          WorkflowGraphValidator.sha256(mapper.writeValueAsString(event.payload()));
      triggers.processEvent(event.eventId(), event.eventType(), fingerprint, event.payload());
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw WorkflowException.invalid("Invalid workflow event envelope");
    }
  }

  public record EventEnvelope(
      String eventId,
      String eventType,
      int eventVersion,
      Instant timestamp,
      String source,
      String tenantId,
      JsonNode payload) {}
}
