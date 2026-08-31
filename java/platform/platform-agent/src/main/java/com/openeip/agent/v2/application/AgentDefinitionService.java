package com.openeip.agent.v2.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentDefinition;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentVersion;
import com.openeip.agent.v2.infrastructure.AgentPlatformStore;
import com.openeip.agent.v2.shared.AgentPlatformException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentDefinitionService {
  private static final Set<String> TYPES =
      Set.of("DOCUMENT", "SQL", "BI", "SEARCH", "WORKFLOW", "CUSTOM");
  private static final Set<String> PATCH_FIELDS =
      Set.of("name", "description", "modelPolicy", "memoryPolicy", "planner", "workers");

  private final AgentPlatformStore store;
  private final ObjectMapper mapper;
  private final Clock clock;

  @Autowired
  public AgentDefinitionService(AgentPlatformStore store, ObjectMapper mapper) {
    this(store, mapper, Clock.systemUTC());
  }

  AgentDefinitionService(AgentPlatformStore store, ObjectMapper mapper, Clock clock) {
    this.store = store;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public AgentDefinition create(String actorId, String name, String type, String description) {
    String validName = AgentPlatformSupport.requiredText(name, "Agent name", 120);
    String validType = AgentPlatformSupport.requiredText(type, "Agent type", 24).toUpperCase();
    if (!TYPES.contains(validType)) {
      throw AgentPlatformException.invalid("Unsupported Agent type");
    }
    if (store.definitionByName(validName).isPresent()) {
      throw AgentPlatformException.conflict("Agent name already exists");
    }
    Instant now = clock.instant();
    ObjectNode draft = mapper.createObjectNode();
    draft.put("agentType", validType);
    draft.put("modelPolicy", "deterministic");
    draft.put("memoryPolicy", "session-none");
    draft.set(
        "planner",
        mapper
            .createObjectNode()
            .put("maxSteps", 32)
            .put("maxWorkers", validType.equals("WORKFLOW") ? 4 : 0));
    AgentDefinition value =
        new AgentDefinition(
            UUID.randomUUID().toString(),
            AgentPlatformStore.TENANT,
            AgentPlatformSupport.uuid(actorId),
            validName,
            validType,
            AgentPlatformSupport.optionalText(description, "description", 1000),
            "DRAFT",
            AgentPlatformSupport.canonical(mapper, draft),
            0,
            null,
            0,
            now,
            now,
            null);
    store.insertDefinition(value);
    return value;
  }

  @Transactional(readOnly = true)
  public List<AgentDefinition> list(String actorId, int limit) {
    AgentPlatformSupport.uuid(actorId);
    return store.listDefinitions(actorId, AgentPlatformSupport.limit(limit));
  }

  @Transactional(readOnly = true)
  public AgentDefinition get(String actorId, String id) {
    return store
        .definition(AgentPlatformSupport.uuid(id), AgentPlatformSupport.uuid(actorId))
        .orElseThrow(AgentPlatformException::notFound);
  }

  @Transactional
  public AgentDefinition update(String actorId, String id, String ifMatch, JsonNode patch) {
    AgentPlatformSupport.requireAllowedFields(patch, PATCH_FIELDS);
    AgentDefinition current = requireOwner(actorId, id);
    long revision = AgentPlatformSupport.revision(ifMatch);
    if (revision != current.revision() || "ARCHIVED".equals(current.status())) {
      throw AgentPlatformException.precondition("Agent draft revision is stale");
    }
    ObjectNode draft = (ObjectNode) AgentPlatformSupport.read(mapper, current.draftJson());
    String name =
        patch.has("name")
            ? AgentPlatformSupport.requiredText(patch.get("name").asText(null), "Agent name", 120)
            : current.name();
    String description =
        patch.has("description")
            ? AgentPlatformSupport.optionalText(
                patch.get("description").asText(null), "description", 1000)
            : current.description();
    for (String field : List.of("modelPolicy", "memoryPolicy", "planner", "workers")) {
      if (patch.has(field)) {
        draft.set(field, patch.get(field));
      }
    }
    String canonical = AgentPlatformSupport.canonical(mapper, draft);
    if (!store.updateDefinition(
        id,
        revision,
        name,
        description,
        canonical,
        current.publishedVersion() == null ? "DRAFT" : "PUBLISHED",
        current.publishedVersion(),
        null,
        clock.instant())) {
      throw AgentPlatformException.precondition("Agent draft revision is stale");
    }
    return get(actorId, id);
  }

  @Transactional(readOnly = true)
  public List<AgentVersion> versions(String actorId, String id) {
    get(actorId, id);
    return store.versions(id);
  }

  @Transactional
  public AgentVersion createCandidate(
      String actorId, String id, String ifMatch, String idempotencyKey) {
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    AgentDefinition current = requireOwner(actorId, id);
    long revision = AgentPlatformSupport.revision(ifMatch);
    if (revision != current.revision() || "ARCHIVED".equals(current.status())) {
      throw AgentPlatformException.precondition("Agent draft revision is stale");
    }
    String digest = AgentPlatformSupport.sha256(current.draftJson());
    var existing =
        store.versions(id).stream()
            .filter(value -> "CANDIDATE".equals(value.status()))
            .filter(value -> value.sourceDraftRevision() == current.draftRevision())
            .filter(value -> value.digest().equals(digest))
            .findFirst();
    if (existing.isPresent()) {
      return existing.get();
    }
    Instant now = clock.instant();
    AgentVersion candidate =
        new AgentVersion(
            UUID.randomUUID().toString(),
            AgentPlatformStore.TENANT,
            id,
            null,
            "CANDIDATE",
            current.draftRevision(),
            digest,
            current.draftJson(),
            null,
            actorId,
            now,
            null,
            null);
    store.insertVersion(candidate);
    return candidate;
  }

  @Transactional
  public AgentVersion publish(
      String actorId, String id, String ifMatch, String idempotencyKey, String evaluationRunId) {
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    AgentDefinition current = requireOwner(actorId, id);
    long revision = AgentPlatformSupport.revision(ifMatch);
    if (revision != current.revision() || "ARCHIVED".equals(current.status())) {
      throw AgentPlatformException.precondition("Agent draft revision is stale");
    }
    var evaluation =
        store
            .evaluationRun(
                AgentPlatformSupport.uuid(evaluationRunId), AgentPlatformSupport.uuid(actorId))
            .orElseThrow(() -> AgentPlatformException.conflict("Evaluation run was not found"));
    if (!"COMPLETED".equals(evaluation.status()) || !"PASS".equals(evaluation.gateStatus())) {
      throw AgentPlatformException.conflict("A passing Evaluation run is required");
    }
    AgentVersion candidate =
        store
            .versionById(evaluation.candidateAgentVersionId())
            .orElseThrow(
                () -> AgentPlatformException.conflict("Evaluated candidate was not found"));
    String draftDigest = AgentPlatformSupport.sha256(current.draftJson());
    if (!candidate.agentId().equals(id)
        || !"CANDIDATE".equals(candidate.status())
        || candidate.sourceDraftRevision() != current.draftRevision()
        || !candidate.digest().equals(draftDigest)) {
      throw AgentPlatformException.conflict(
          "Evaluation must pass for a candidate snapshot of the current draft");
    }
    int number = current.publishedVersion() == null ? 1 : current.publishedVersion() + 1;
    Instant now = clock.instant();
    if (!store.promoteCandidate(candidate.id(), evaluationRunId, number, actorId, now)) {
      throw AgentPlatformException.conflict("Agent candidate is no longer publishable");
    }
    if (!store.updateDefinition(
        id,
        revision,
        current.name(),
        current.description(),
        current.draftJson(),
        "PUBLISHED",
        number,
        null,
        now)) {
      throw AgentPlatformException.precondition("Agent draft revision is stale");
    }
    return store.version(id, number).orElseThrow();
  }

  @Transactional
  public AgentDefinition restore(
      String actorId, String id, int version, String ifMatch, String idempotencyKey) {
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    AgentDefinition current = requireOwner(actorId, id);
    long revision = AgentPlatformSupport.revision(ifMatch);
    AgentVersion source = store.version(id, version).orElseThrow(AgentPlatformException::notFound);
    if (!store.updateDefinition(
        id,
        revision,
        current.name(),
        current.description(),
        source.configJson(),
        "PUBLISHED",
        current.publishedVersion(),
        null,
        clock.instant())) {
      throw AgentPlatformException.precondition("Agent draft revision is stale");
    }
    return get(actorId, id);
  }

  @Transactional
  public AgentDefinition archive(String actorId, String id, String ifMatch, String idempotencyKey) {
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    AgentDefinition current = requireOwner(actorId, id);
    long revision = AgentPlatformSupport.revision(ifMatch);
    Instant now = clock.instant();
    if (!store.updateDefinition(
        id,
        revision,
        current.name(),
        current.description(),
        current.draftJson(),
        "ARCHIVED",
        current.publishedVersion(),
        now,
        now)) {
      throw AgentPlatformException.precondition("Agent draft revision is stale");
    }
    return get(actorId, id);
  }

  private AgentDefinition requireOwner(String actorId, String id) {
    AgentDefinition value = get(actorId, id);
    if (!value.ownerId().equals(actorId)) {
      throw AgentPlatformException.forbidden();
    }
    return value;
  }
}
