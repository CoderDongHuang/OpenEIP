package com.openeip.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.domain.entity.WorkflowExecution;
import com.openeip.workflow.domain.entity.WorkflowProcessedEvent;
import com.openeip.workflow.domain.entity.WorkflowTrigger;
import com.openeip.workflow.domain.repository.WorkflowProcessedEventRepository;
import com.openeip.workflow.domain.repository.WorkflowTriggerRepository;
import com.openeip.workflow.shared.WorkflowException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowTriggerService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern EVENT_TYPE =
      Pattern.compile("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+){1,7}$");
  private final WorkflowService workflows;
  private final WorkflowExecutionService executions;
  private final WorkflowTriggerRepository triggers;
  private final WorkflowProcessedEventRepository processedEvents;
  private final WorkflowWebhookRateLimiter webhookRateLimiter;
  private final ObjectMapper mapper;
  private final Clock clock = Clock.systemUTC();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are shared services.")
  public WorkflowTriggerService(
      WorkflowService workflows,
      WorkflowExecutionService executions,
      WorkflowTriggerRepository triggers,
      WorkflowProcessedEventRepository processedEvents,
      WorkflowWebhookRateLimiter webhookRateLimiter,
      ObjectMapper mapper) {
    this.workflows = workflows;
    this.executions = executions;
    this.triggers = triggers;
    this.processedEvents = processedEvents;
    this.webhookRateLimiter = webhookRateLimiter;
    this.mapper = mapper;
  }

  @Transactional
  public CreatedTrigger create(
      String userId, String workflowId, String type, boolean enabled, JsonNode config) {
    WorkflowService.WorkflowAccess access = workflows.get(userId, workflowId);
    WorkflowService.requireEditor(access.role());
    if (access.workflow().getPublishedVersion() == null) {
      throw WorkflowException.conflict("Publish the workflow before creating triggers");
    }
    if (config == null || !config.isObject() || config.size() > 20) {
      throw WorkflowException.invalid("Trigger config must be a bounded object");
    }
    Instant now = clock.instant();
    String id = UUID.randomUUID().toString();
    String secret = null;
    String secretHash = null;
    Instant nextFireAt = null;
    if (type.equals("WEBHOOK") && config.isEmpty()) {
      byte[] bytes = new byte[32];
      RANDOM.nextBytes(bytes);
      secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      secretHash = WorkflowGraphValidator.sha256(secret);
    } else if (type.equals("CRON")) {
      if (!hasOnlyFields(config, Set.of("expression"))
          || !config.path("expression").isTextual()
          || config.path("expression").asText().trim().split("\\s+").length != 5) {
        throw WorkflowException.invalid("Invalid UTC cron expression");
      }
      String expression = config.path("expression").asText();
      try {
        nextFireAt = next(expression, now);
      } catch (IllegalArgumentException exception) {
        throw WorkflowException.invalid("Invalid UTC cron expression");
      }
    } else if (!type.equals("EVENT")
        || !hasOnlyFields(config, Set.of("eventType"))
        || !config.path("eventType").isTextual()
        || config.path("eventType").asText().length() > 128
        || !EVENT_TYPE.matcher(config.path("eventType").asText()).matches()) {
      throw WorkflowException.invalid("Invalid trigger type or configuration");
    }
    WorkflowTrigger trigger =
        triggers.save(
            new WorkflowTrigger(
                id,
                WorkflowService.TENANT,
                workflowId,
                type,
                enabled,
                json(config),
                secretHash,
                nextFireAt,
                now));
    return new CreatedTrigger(trigger, secret);
  }

  @Transactional(readOnly = true)
  public List<WorkflowTrigger> list(String userId, String workflowId) {
    workflows.get(userId, workflowId);
    return triggers.findAllByTenantIdAndWorkflowIdOrderByCreatedAtDesc(
        WorkflowService.TENANT, workflowId);
  }

  @Transactional
  public WorkflowExecution invoke(
      String hookId, String secret, String idempotencyKey, JsonNode input) {
    WorkflowService.validUuid(hookId);
    if (!webhookRateLimiter.acquire(hookId)) {
      throw WorkflowException.rateLimited();
    }
    WorkflowTrigger trigger =
        triggers
            .findByIdAndEnabledTrue(hookId)
            .filter(value -> value.getType().equals("WEBHOOK"))
            .orElseThrow(WorkflowException::notFound);
    if (secret == null
        || !MessageDigest.isEqual(
            trigger.getSecretHash().getBytes(StandardCharsets.US_ASCII),
            WorkflowGraphValidator.sha256(secret).getBytes(StandardCharsets.US_ASCII))) {
      throw WorkflowException.unauthorized();
    }
    return executions.triggerSystem(trigger.getWorkflowId(), idempotencyKey, input, "WEBHOOK");
  }

  @Transactional
  public void fireDue() {
    Instant now = clock.instant();
    for (WorkflowTrigger trigger :
        triggers.findTop50ByTypeAndEnabledTrueAndNextFireAtBeforeOrderByNextFireAtAsc(
            "CRON", now)) {
      Instant scheduled = trigger.getNextFireAt();
      executions.triggerSystem(
          trigger.getWorkflowId(),
          trigger.getId() + ":" + scheduled.toEpochMilli(),
          mapper.createObjectNode(),
          "CRON");
      JsonNode config = read(trigger.getConfigJson());
      trigger.nextFireAt(next(config.path("expression").asText(), scheduled));
    }
  }

  @Transactional
  public int processEvent(String eventId, String eventType, String fingerprint, JsonNode payload) {
    WorkflowService.validUuid(eventId);
    var duplicate = processedEvents.findByTenantIdAndEventId(WorkflowService.TENANT, eventId);
    if (duplicate.isPresent()) {
      if (!duplicate.get().getPayloadFingerprint().equals(fingerprint)) {
        throw WorkflowException.conflict("Event identifier was reused with a different payload");
      }
      return 0;
    }
    processedEvents.save(
        new WorkflowProcessedEvent(
            UUID.randomUUID().toString(),
            WorkflowService.TENANT,
            eventId,
            eventType,
            fingerprint,
            clock.instant()));
    int started = 0;
    for (WorkflowTrigger trigger : triggers.findAllByTypeAndEnabledTrue("EVENT")) {
      JsonNode config = read(trigger.getConfigJson());
      if (config.path("eventType").asText().equals(eventType)) {
        executions.triggerSystem(
            trigger.getWorkflowId(), eventId + ":" + trigger.getId(), payload, "EVENT");
        started++;
      }
    }
    return started;
  }

  private static Instant next(String expression, Instant after) {
    String normalized = "0 " + expression.trim();
    ZonedDateTime value =
        CronExpression.parse(normalized).next(ZonedDateTime.ofInstant(after, ZoneOffset.UTC));
    if (value == null) {
      throw new IllegalArgumentException("Cron expression has no next time");
    }
    return value.toInstant();
  }

  private static boolean hasOnlyFields(JsonNode value, Set<String> allowed) {
    Iterator<String> names = value.fieldNames();
    while (names.hasNext()) {
      if (!allowed.contains(names.next())) {
        return false;
      }
    }
    return true;
  }

  private String json(JsonNode value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw WorkflowException.invalid("Invalid trigger configuration");
    }
  }

  private JsonNode read(String value) {
    try {
      return mapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored trigger configuration is invalid", exception);
    }
  }

  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "Transaction-scoped entity.")
  public record CreatedTrigger(WorkflowTrigger trigger, String secret) {}
}
