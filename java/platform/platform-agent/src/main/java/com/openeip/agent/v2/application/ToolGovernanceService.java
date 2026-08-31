package com.openeip.agent.v2.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.domain.AgentPlatformModels.ToolGrant;
import com.openeip.agent.v2.domain.AgentPlatformModels.ToolVersion;
import com.openeip.agent.v2.infrastructure.AgentPlatformStore;
import com.openeip.agent.v2.shared.AgentPlatformException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolGovernanceService {
  private static final Set<String> APPROVAL_MODES = Set.of("NONE", "POLICY", "PER_CALL");
  private final AgentPlatformStore store;
  private final ObjectMapper mapper;
  private final Clock clock;

  @Autowired
  public ToolGovernanceService(AgentPlatformStore store, ObjectMapper mapper) {
    this(store, mapper, Clock.systemUTC());
  }

  ToolGovernanceService(AgentPlatformStore store, ObjectMapper mapper, Clock clock) {
    this.store = store;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<ToolVersion> tools(int limit) {
    return store.tools(AgentPlatformSupport.limit(limit));
  }

  @Transactional(readOnly = true)
  public List<ToolGrant> grants(int limit) {
    return store.grants(AgentPlatformSupport.limit(limit));
  }

  public void validateIdempotency(String actorId, String idempotencyKey) {
    AgentPlatformSupport.uuid(actorId);
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
  }

  @Transactional
  public ToolGrant createGrant(
      String actorId,
      String idempotencyKey,
      String agentVersionId,
      String toolVersionId,
      JsonNode operations,
      JsonNode resourceSelector,
      JsonNode argumentConstraints,
      String approvalMode,
      String expiresAt) {
    AgentPlatformSupport.uuid(actorId);
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    var agent =
        store
            .versionById(AgentPlatformSupport.uuid(agentVersionId))
            .orElseThrow(AgentPlatformException::notFound);
    ToolVersion tool =
        store
            .tool(AgentPlatformSupport.uuid(toolVersionId))
            .orElseThrow(AgentPlatformException::notFound);
    List<String> requested = AgentPlatformSupport.stringList(operations, 32, "operations");
    Set<String> supported =
        new HashSet<>(
            AgentPlatformSupport.stringList(
                AgentPlatformSupport.read(mapper, tool.operationsJson()), 32, "Tool operations"));
    if (!supported.containsAll(requested)) {
      throw AgentPlatformException.invalid("Grant contains unsupported Tool operations");
    }
    String mode =
        AgentPlatformSupport.requiredText(approvalMode, "approval mode", 16).toUpperCase();
    if (!APPROVAL_MODES.contains(mode)
        || ("DESTRUCTIVE".equals(tool.riskClass()) && !"PER_CALL".equals(mode))) {
      throw AgentPlatformException.invalid("Approval mode does not satisfy Tool risk");
    }
    Instant expiry = AgentPlatformSupport.instant(expiresAt, "grant expiry");
    if (expiry != null && !expiry.isAfter(clock.instant())) {
      throw AgentPlatformException.invalid("Grant expiry must be in the future");
    }
    String id = UUID.randomUUID().toString();
    ToolGrant value =
        new ToolGrant(
            id,
            agent.id(),
            tool.id(),
            AgentPlatformSupport.canonical(mapper, operations),
            AgentPlatformSupport.canonical(
                mapper, resourceSelector == null ? mapper.createObjectNode() : resourceSelector),
            AgentPlatformSupport.canonical(
                mapper,
                argumentConstraints == null ? mapper.createObjectNode() : argumentConstraints),
            mode,
            expiry,
            0,
            clock.instant(),
            null);
    store.insertGrant(value, actorId);
    return value;
  }

  @Transactional
  public ToolGrant revoke(String actorId, String id, String ifMatch, String idempotencyKey) {
    AgentPlatformSupport.uuid(actorId);
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    long revision = AgentPlatformSupport.revision(ifMatch);
    ToolGrant current =
        store.grants(100).stream()
            .filter(value -> value.id().equals(AgentPlatformSupport.uuid(id)))
            .findFirst()
            .orElseThrow(AgentPlatformException::notFound);
    if (!store.revokeGrant(id, revision, clock.instant())) {
      throw AgentPlatformException.precondition("Tool grant revision is stale");
    }
    return new ToolGrant(
        current.id(),
        current.agentVersionId(),
        current.toolVersionId(),
        current.operationsJson(),
        current.resourceSelectorJson(),
        current.argumentConstraintsJson(),
        current.approvalMode(),
        current.expiresAt(),
        current.revision() + 1,
        current.createdAt(),
        clock.instant());
  }
}
