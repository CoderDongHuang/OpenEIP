package com.openeip.agent.v2.application;

import com.openeip.agent.v2.domain.AgentPlatformModels.MemoryEntry;
import com.openeip.agent.v2.infrastructure.AgentPlatformStore;
import com.openeip.agent.v2.shared.AgentPlatformException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryGovernanceService {
  private final AgentPlatformStore store;
  private final Clock clock;

  @Autowired
  public MemoryGovernanceService(AgentPlatformStore store) {
    this(store, Clock.systemUTC());
  }

  MemoryGovernanceService(AgentPlatformStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<MemoryEntry> list(String actorId, String purpose, String state, int limit) {
    AgentPlatformSupport.uuid(actorId);
    String safePurpose =
        purpose == null || purpose.isBlank()
            ? null
            : AgentPlatformSupport.requiredText(purpose, "Memory purpose", 64);
    String safeState = state == null || state.isBlank() ? null : state.toUpperCase();
    if (safeState != null
        && !java.util.Set.of("ACTIVE", "QUARANTINED", "DELETING", "DELETED").contains(safeState)) {
      throw AgentPlatformException.invalid("Invalid Memory state");
    }
    return store.memories(actorId, safePurpose, safeState, AgentPlatformSupport.limit(limit));
  }

  @Transactional(readOnly = true)
  public MemoryEntry get(String actorId, String id) {
    return store
        .memory(AgentPlatformSupport.uuid(id), AgentPlatformSupport.uuid(actorId))
        .orElseThrow(AgentPlatformException::notFound);
  }

  @Transactional
  public MemoryEntry quarantine(String actorId, String id, String ifMatch, String idempotencyKey) {
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    MemoryEntry current = get(actorId, id);
    if (!"ACTIVE".equals(current.state())) {
      throw AgentPlatformException.conflict("Only active Memory can be quarantined");
    }
    if (!store.updateMemoryState(
        id, actorId, AgentPlatformSupport.revision(ifMatch), "QUARANTINED", null)) {
      throw AgentPlatformException.precondition("Memory revision is stale");
    }
    return get(actorId, id);
  }

  @Transactional
  public MemoryEntry delete(String actorId, String id, String ifMatch, String idempotencyKey) {
    String key = AgentPlatformSupport.idempotencyKey(idempotencyKey);
    MemoryEntry current = get(actorId, id);
    if ("DELETED".equals(current.state()) || "DELETING".equals(current.state())) {
      return current;
    }
    if (!store.updateMemoryState(
        id, actorId, AgentPlatformSupport.revision(ifMatch), "DELETING", clock.instant())) {
      throw AgentPlatformException.precondition("Memory revision is stale");
    }
    store.insertPurgeJob(UUID.randomUUID().toString(), id, key, clock.instant());
    return get(actorId, id);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> exportMetadata(
      String actorId, String purpose, boolean includeContent, int limit) {
    if (includeContent) {
      throw AgentPlatformException.forbidden();
    }
    List<MemoryEntry> entries = list(actorId, purpose, null, Math.min(limit, 100));
    return Map.of(
        "exportId",
        UUID.randomUUID().toString(),
        "contentIncluded",
        false,
        "count",
        entries.size(),
        "items",
        entries);
  }
}
