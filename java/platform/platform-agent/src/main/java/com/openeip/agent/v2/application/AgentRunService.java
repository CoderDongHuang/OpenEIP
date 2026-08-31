package com.openeip.agent.v2.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentDefinition;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentRun;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentRunEvent;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentVersion;
import com.openeip.agent.v2.domain.AgentPlatformModels.ToolGrant;
import com.openeip.agent.v2.domain.AgentPlatformModels.ToolVersion;
import com.openeip.agent.v2.infrastructure.AgentPlatformStore;
import com.openeip.agent.v2.infrastructure.AgentRuntimeV2Gateway;
import com.openeip.agent.v2.shared.AgentPlatformException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunService {
  private static final Set<String> STATES =
      Set.of(
          "QUEUED",
          "PLANNING",
          "EXECUTING",
          "REFLECTING",
          "PAUSED",
          "SUCCEEDED",
          "FAILED",
          "CANCELLED");
  private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED", "CANCELLED");
  private static final Set<String> SAFE_EVENT_TYPES =
      Set.of(
          "run.queued",
          "run.planning",
          "plan.created",
          "step.started",
          "tool.started",
          "tool.completed",
          "worker.started",
          "handoff.created",
          "tool.approval.required",
          "tool.approval.decided",
          "reflection.completed",
          "run.paused",
          "run.resumed",
          "run.completed",
          "run.failed",
          "run.cancelled");

  private final AgentPlatformStore store;
  private final AgentDefinitionService definitions;
  private final AgentCapabilityService capabilities;
  private final AgentRuntimeV2Gateway runtime;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final ConcurrentMap<String, String> transientInputs = new ConcurrentHashMap<>();

  @Autowired
  public AgentRunService(
      AgentPlatformStore store,
      AgentDefinitionService definitions,
      AgentCapabilityService capabilities,
      AgentRuntimeV2Gateway runtime,
      ObjectMapper mapper) {
    this(store, definitions, capabilities, runtime, mapper, Clock.systemUTC());
  }

  AgentRunService(
      AgentPlatformStore store,
      AgentDefinitionService definitions,
      AgentCapabilityService capabilities,
      AgentRuntimeV2Gateway runtime,
      ObjectMapper mapper,
      Clock clock) {
    this.store = store;
    this.definitions = definitions;
    this.capabilities = capabilities;
    this.runtime = runtime;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public AgentRun create(
      String actorId,
      String agentId,
      String idempotencyKey,
      int agentVersion,
      String input,
      JsonNode resourceHandles,
      JsonNode budget) {
    AgentPlatformSupport.uuid(actorId);
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    AgentDefinition definition = definitions.get(actorId, AgentPlatformSupport.uuid(agentId));
    if ("ARCHIVED".equals(definition.status())) {
      throw AgentPlatformException.conflict("Archived Agent cannot start new runs");
    }
    AgentVersion version =
        store.version(agentId, agentVersion).orElseThrow(AgentPlatformException::notFound);
    String validInput = AgentPlatformSupport.requiredText(input, "Agent input", 32_000);
    List<String> handles = resourceHandles(resourceHandles);
    JsonNode resolvedBudget = budget(budget);
    List<ToolGrant> grants = store.activeGrants(version.id(), clock.instant());
    List<ToolVersion> tools =
        grants.stream().map(value -> store.tool(value.toolVersionId()).orElseThrow()).toList();
    String dependencyDigest =
        AgentPlatformSupport.sha256(
            version.digest()
                + "|"
                + grants.stream()
                    .map(value -> value.id() + ":" + value.toolVersionId())
                    .sorted()
                    .toList());
    Instant now = clock.instant();
    String runId = UUID.randomUUID().toString();
    AgentRun run =
        new AgentRun(
            runId,
            agentId,
            version.id(),
            version.version(),
            actorId,
            "QUEUED",
            AgentPlatformSupport.sha256(validInput),
            json(handles),
            AgentPlatformSupport.canonical(mapper, resolvedBudget),
            dependencyDigest,
            0,
            0,
            null,
            now,
            now,
            null);
    store.insertRun(run);
    append(runId, "run.queued", Map.of("agentVersion", agentVersion));
    transientInputs.put(runId, validInput);
    return execute(actorId, runId, definition, version, grants, tools);
  }

  @Transactional(readOnly = true)
  public List<AgentRun> list(String actorId, String status, int limit) {
    AgentPlatformSupport.uuid(actorId);
    String resolved = status == null || status.isBlank() ? null : status.toUpperCase();
    if (resolved != null && !STATES.contains(resolved)) {
      throw AgentPlatformException.invalid("Invalid Agent run status");
    }
    return store.runs(actorId, resolved, AgentPlatformSupport.limit(limit));
  }

  @Transactional(readOnly = true)
  public AgentRun get(String actorId, String runId) {
    return store
        .run(AgentPlatformSupport.uuid(runId), AgentPlatformSupport.uuid(actorId))
        .orElseThrow(AgentPlatformException::notFound);
  }

  @Transactional(readOnly = true)
  public List<AgentRunEvent> events(String actorId, String runId, long after, int limit) {
    get(actorId, runId);
    if (after < 0) {
      throw AgentPlatformException.invalid("Event sequence cannot be negative");
    }
    return store.runEvents(runId, after, AgentPlatformSupport.limit(limit));
  }

  @Transactional
  public AgentRun command(
      String actorId, String runId, String command, String ifMatch, String idempotencyKey) {
    AgentRun run = get(actorId, runId);
    long revision = AgentPlatformSupport.revision(ifMatch);
    String key = AgentPlatformSupport.idempotencyKey(idempotencyKey);
    if (run.revision() != revision) {
      throw AgentPlatformException.precondition("Agent run revision is stale");
    }
    if (!store.recordCommand(
        UUID.randomUUID().toString(), run.id(), command, key, revision, actorId, clock.instant())) {
      return get(actorId, runId);
    }
    return switch (command) {
      case "CANCEL" -> cancel(run);
      case "PAUSE" -> pause(run);
      case "RESUME" -> resume(run);
      case "RETRY" -> retry(actorId, run);
      default -> throw AgentPlatformException.invalid("Unknown Agent command");
    };
  }

  @Transactional
  public AgentRun retryStep(
      String actorId, String runId, String stepId, String ifMatch, String idempotencyKey) {
    AgentPlatformSupport.uuid(stepId);
    return command(actorId, runId, "RETRY", ifMatch, idempotencyKey);
  }

  @Transactional
  public AgentRun decideApproval(
      String actorId,
      String runId,
      String approvalId,
      String decision,
      String reason,
      String ifMatch,
      String idempotencyKey) {
    AgentRun run = get(actorId, runId);
    long revision = AgentPlatformSupport.revision(ifMatch);
    String key = AgentPlatformSupport.idempotencyKey(idempotencyKey);
    String safeDecision =
        AgentPlatformSupport.requiredText(decision, "approval decision", 16).toUpperCase();
    String safeReason = AgentPlatformSupport.optionalText(reason, "approval reason", 500);
    AgentRunEvent approval =
        store
            .runEvent(AgentPlatformSupport.uuid(approvalId), run.id())
            .filter(value -> "tool.approval.required".equals(value.type()))
            .orElseThrow(AgentPlatformException::notFound);
    if (!"PAUSED".equals(run.status()) || run.revision() != revision) {
      throw AgentPlatformException.precondition("Agent approval revision is stale");
    }
    if (!Set.of("APPROVE", "REJECT").contains(safeDecision)) {
      throw AgentPlatformException.invalid("Invalid Tool approval decision");
    }
    if (!store.recordCommand(
        UUID.randomUUID().toString(),
        run.id(),
        safeDecision,
        key,
        revision,
        actorId,
        clock.instant())) {
      return get(actorId, runId);
    }
    append(
        run.id(),
        "tool.approval.decided",
        Map.of(
            "approvalId",
            approval.id(),
            "decision",
            safeDecision,
            "reasonProvided",
            !safeReason.isBlank()));
    transition(
        get(actorId, runId),
        "APPROVE".equals(safeDecision) ? "QUEUED" : "CANCELLED",
        null,
        "REJECT".equals(safeDecision) ? clock.instant() : null);
    return get(actorId, runId);
  }

  private AgentRun execute(
      String actorId,
      String runId,
      AgentDefinition definition,
      AgentVersion version,
      List<ToolGrant> grants,
      List<ToolVersion> tools) {
    AgentRun run = get(actorId, runId);
    transition(run, "PLANNING", null, null);
    append(runId, "run.planning", Map.of());
    run = get(actorId, runId);
    try {
      String capability = capabilities.issue(run, version.digest(), grants, tools);
      var result =
          runtime.execute(
              capability,
              transientInputs.getOrDefault(runId, ""),
              definition.type(),
              version.version());
      for (var event : result.events()) {
        if (!SAFE_EVENT_TYPES.contains(event.type())) {
          throw AgentPlatformException.upstream();
        }
        String payload = AgentPlatformSupport.canonical(mapper, event.payload());
        if (payload.length() > 65_536 || containsSensitiveKey(event.payload())) {
          throw AgentPlatformException.upstream();
        }
        store.appendRunEvent(
            runId, UUID.randomUUID().toString(), event.type(), payload, clock.instant());
      }
      run = get(actorId, runId);
      String status = "SUCCEEDED".equals(result.status()) ? "SUCCEEDED" : "FAILED";
      transition(run, status, result.failureCode(), clock.instant());
      append(
          runId,
          status.equals("SUCCEEDED") ? "run.completed" : "run.failed",
          status.equals("SUCCEEDED")
              ? Map.of("result", "completed")
              : Map.of("code", safeFailure(result.failureCode())));
      if (status.equals("SUCCEEDED")) {
        transientInputs.remove(runId);
      }
      return get(actorId, runId);
    } catch (RuntimeException exception) {
      run = get(actorId, runId);
      if (!TERMINAL.contains(run.status())) {
        transition(run, "FAILED", "AGENT2-S-001", clock.instant());
        append(runId, "run.failed", Map.of("code", "AGENT2-S-001"));
      }
      return get(actorId, runId);
    }
  }

  private AgentRun cancel(AgentRun run) {
    if (!TERMINAL.contains(run.status())) {
      transition(run, "CANCELLED", null, clock.instant());
      append(run.id(), "run.cancelled", Map.of());
      transientInputs.remove(run.id());
    }
    return get(run.principalId(), run.id());
  }

  private AgentRun pause(AgentRun run) {
    if (TERMINAL.contains(run.status()) || "PAUSED".equals(run.status())) {
      throw AgentPlatformException.conflict("Agent run cannot be paused");
    }
    transition(run, "PAUSED", null, null);
    append(run.id(), "run.paused", Map.of());
    return get(run.principalId(), run.id());
  }

  private AgentRun resume(AgentRun run) {
    if (!"PAUSED".equals(run.status())) {
      throw AgentPlatformException.conflict("Only a paused Agent run can resume");
    }
    transition(run, "QUEUED", null, null);
    append(run.id(), "run.resumed", Map.of());
    return get(run.principalId(), run.id());
  }

  private AgentRun retry(String actorId, AgentRun run) {
    if (!"FAILED".equals(run.status()) || !transientInputs.containsKey(run.id())) {
      throw AgentPlatformException.conflict(
          "Agent run cannot be retried from a durable safe checkpoint");
    }
    AgentDefinition definition = definitions.get(actorId, run.agentId());
    AgentVersion version = store.versionById(run.agentVersionId()).orElseThrow();
    List<ToolGrant> grants = store.activeGrants(version.id(), clock.instant());
    List<ToolVersion> tools =
        grants.stream().map(value -> store.tool(value.toolVersionId()).orElseThrow()).toList();
    transition(run, "QUEUED", null, null);
    return execute(actorId, run.id(), definition, version, grants, tools);
  }

  private void transition(AgentRun run, String status, String failureCode, Instant completedAt) {
    if (!store.transitionRun(
        run.id(), run.revision(), status, failureCode, completedAt, clock.instant())) {
      throw AgentPlatformException.precondition("Agent run revision is stale");
    }
  }

  private void append(String runId, String type, Object payload) {
    store.appendRunEvent(runId, UUID.randomUUID().toString(), type, json(payload), clock.instant());
  }

  private JsonNode budget(JsonNode value) {
    JsonNode source = value == null || value.isNull() ? mapper.createObjectNode() : value;
    AgentPlatformSupport.requireAllowedFields(
        source, Set.of("maxSteps", "maxDurationSeconds", "maxToolCalls", "maxWorkers"));
    int steps = bounded(source.path("maxSteps").asInt(32), 1, 64, "maxSteps");
    int duration =
        bounded(source.path("maxDurationSeconds").asInt(600), 1, 1800, "maxDurationSeconds");
    int calls = bounded(source.path("maxToolCalls").asInt(64), 0, 128, "maxToolCalls");
    int workers = bounded(source.path("maxWorkers").asInt(4), 0, 16, "maxWorkers");
    return mapper
        .createObjectNode()
        .put("maxSteps", steps)
        .put("maxDurationSeconds", duration)
        .put("maxToolCalls", calls)
        .put("maxWorkers", workers);
  }

  private List<String> resourceHandles(JsonNode value) {
    if (value == null || value.isNull()) {
      return List.of();
    }
    if (!value.isArray() || value.size() > 64) {
      throw AgentPlatformException.invalid("Invalid resource handles");
    }
    Set<String> result = new HashSet<>();
    for (JsonNode handle : value) {
      if (!handle.isTextual()
          || !handle.textValue().matches("^(document|knowledge|connector|workflow):[0-9a-f-]{36}$")
          || !result.add(handle.textValue())) {
        throw AgentPlatformException.invalid("Invalid resource handles");
      }
    }
    return result.stream().sorted().toList();
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Safe Agent value is not serializable", exception);
    }
  }

  private static int bounded(int value, int min, int max, String field) {
    if (value < min || value > max) {
      throw AgentPlatformException.invalid("Invalid " + field);
    }
    return value;
  }

  private static boolean containsSensitiveKey(JsonNode value) {
    if (value.isObject()) {
      var fields = value.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        String key = field.getKey().toLowerCase();
        if (key.contains("secret")
            || key.contains("token")
            || key.contains("credential")
            || key.contains("prompt")
            || key.contains("thought")
            || containsSensitiveKey(field.getValue())) {
          return true;
        }
      }
    } else if (value.isArray()) {
      for (JsonNode item : value) {
        if (containsSensitiveKey(item)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String safeFailure(String value) {
    return value != null && value.matches("^AGENT2-[A-Z]-[0-9]{3}$") ? value : "AGENT2-S-001";
  }
}
