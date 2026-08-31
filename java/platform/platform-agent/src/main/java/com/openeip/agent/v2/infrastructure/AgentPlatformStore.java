package com.openeip.agent.v2.infrastructure;

import com.openeip.agent.v2.domain.AgentPlatformModels.AgentDefinition;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentRun;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentRunEvent;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentVersion;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationCase;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationDataset;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationRun;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationSuite;
import com.openeip.agent.v2.domain.AgentPlatformModels.Gate;
import com.openeip.agent.v2.domain.AgentPlatformModels.McpCapability;
import com.openeip.agent.v2.domain.AgentPlatformModels.McpServer;
import com.openeip.agent.v2.domain.AgentPlatformModels.MemoryEntry;
import com.openeip.agent.v2.domain.AgentPlatformModels.Metric;
import com.openeip.agent.v2.domain.AgentPlatformModels.ToolGrant;
import com.openeip.agent.v2.domain.AgentPlatformModels.ToolVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "The application-scoped JdbcTemplate is intentionally injected and shared.")
public class AgentPlatformStore {
  public static final String TENANT = "default";
  private final JdbcTemplate jdbc;

  public AgentPlatformStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertDefinition(AgentDefinition value) {
    jdbc.update(
        """
        INSERT INTO agent_definition
          (id, tenant_id, owner_id, name, agent_type, description, status, draft_json,
           draft_revision, published_version, revision, created_at, updated_at, archived_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        value.id(),
        value.tenantId(),
        value.ownerId(),
        value.name(),
        value.type(),
        value.description(),
        value.status(),
        value.draftJson(),
        value.draftRevision(),
        value.publishedVersion(),
        value.revision(),
        timestamp(value.createdAt()),
        timestamp(value.updatedAt()),
        timestamp(value.archivedAt()));
  }

  public List<AgentDefinition> listDefinitions(String actorId, int limit) {
    return jdbc.query(
        """
        SELECT * FROM agent_definition
        WHERE tenant_id = ? AND (owner_id = ? OR owner_id = 'system')
        ORDER BY updated_at DESC, id DESC LIMIT ?
        """,
        AgentPlatformStore::definition,
        TENANT,
        actorId,
        limit);
  }

  public Optional<AgentDefinition> definition(String id, String actorId) {
    return jdbc
        .query(
            """
            SELECT * FROM agent_definition
            WHERE tenant_id = ? AND id = ? AND (owner_id = ? OR owner_id = 'system')
            """,
            AgentPlatformStore::definition,
            TENANT,
            id,
            actorId)
        .stream()
        .findFirst();
  }

  public Optional<AgentDefinition> definitionByName(String name) {
    return jdbc
        .query(
            "SELECT * FROM agent_definition WHERE tenant_id = ? AND name = ?",
            AgentPlatformStore::definition,
            TENANT,
            name)
        .stream()
        .findFirst();
  }

  public boolean updateDefinition(
      String id,
      long expectedRevision,
      String name,
      String description,
      String draftJson,
      String status,
      Integer publishedVersion,
      Instant archivedAt,
      Instant now) {
    return jdbc.update(
            """
            UPDATE agent_definition
            SET name = ?, description = ?, draft_json = ?, status = ?, published_version = ?,
                draft_revision = draft_revision + 1, revision = revision + 1,
                archived_at = ?, updated_at = ?
            WHERE tenant_id = ? AND id = ? AND revision = ?
            """,
            name,
            description,
            draftJson,
            status,
            publishedVersion,
            timestamp(archivedAt),
            timestamp(now),
            TENANT,
            id,
            expectedRevision)
        == 1;
  }

  public void insertVersion(AgentVersion value) {
    jdbc.update(
        """
        INSERT INTO agent_version
          (id, tenant_id, agent_id, version_number, snapshot_status, source_draft_revision,
           content_digest, config_json, evaluation_run_id, created_by, created_at,
           published_by, published_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        value.id(),
        value.tenantId(),
        value.agentId(),
        value.version(),
        value.status(),
        value.sourceDraftRevision(),
        value.digest(),
        value.configJson(),
        value.evaluationRunId(),
        value.createdBy(),
        timestamp(value.createdAt()),
        value.publishedBy(),
        timestamp(value.publishedAt()));
  }

  public List<AgentVersion> versions(String agentId) {
    return jdbc.query(
        """
        SELECT * FROM agent_version WHERE tenant_id = ? AND agent_id = ?
        ORDER BY published_at DESC, created_at DESC
        """,
        AgentPlatformStore::version,
        TENANT,
        agentId);
  }

  public Optional<AgentVersion> version(String agentId, int version) {
    return jdbc
        .query(
            """
            SELECT * FROM agent_version
            WHERE tenant_id = ? AND agent_id = ? AND version_number = ?
              AND snapshot_status = 'PUBLISHED'
            """,
            AgentPlatformStore::version,
            TENANT,
            agentId,
            version)
        .stream()
        .findFirst();
  }

  public boolean promoteCandidate(
      String id, String evaluationRunId, int version, String publishedBy, Instant publishedAt) {
    return jdbc.update(
            """
            UPDATE agent_version
            SET version_number = ?, snapshot_status = 'PUBLISHED', evaluation_run_id = ?,
                published_by = ?, published_at = ?
            WHERE tenant_id = ? AND id = ? AND snapshot_status = 'CANDIDATE'
              AND version_number IS NULL AND evaluation_run_id IS NULL
            """,
            version,
            evaluationRunId,
            publishedBy,
            timestamp(publishedAt),
            TENANT,
            id)
        == 1;
  }

  public Optional<AgentVersion> versionById(String id) {
    return jdbc
        .query(
            "SELECT * FROM agent_version WHERE tenant_id = ? AND id = ?",
            AgentPlatformStore::version,
            TENANT,
            id)
        .stream()
        .findFirst();
  }

  public void insertToolDefinition(
      String id, String key, String name, String description, Instant now) {
    jdbc.update(
        """
        INSERT INTO tool_definition
          (id, tenant_id, tool_key, display_name, description, status, created_at)
        VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)
        """,
        id,
        TENANT,
        key,
        name,
        description,
        timestamp(now));
  }

  public Optional<String> toolDefinitionId(String key) {
    return jdbc
        .query(
            "SELECT id FROM tool_definition WHERE tenant_id = ? AND tool_key = ?",
            (rs, row) -> rs.getString(1),
            TENANT,
            key)
        .stream()
        .findFirst();
  }

  public void insertToolVersion(String definitionId, ToolVersion value, Instant now) {
    jdbc.update(
        """
        INSERT INTO tool_version
          (id, tenant_id, tool_definition_id, semantic_version, content_digest, risk_class,
           idempotency_mode, operations_json, input_schema_json, output_schema_json,
           max_duration_ms, max_result_bytes, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        value.id(),
        TENANT,
        definitionId,
        value.version(),
        value.digest(),
        value.riskClass(),
        value.idempotencyMode(),
        value.operationsJson(),
        value.inputSchemaJson(),
        value.outputSchemaJson(),
        value.maxDurationMs(),
        value.maxResultBytes(),
        timestamp(now));
  }

  public List<ToolVersion> tools(int limit) {
    return jdbc.query(
        """
        SELECT tv.*, td.tool_key, td.display_name, td.description
        FROM tool_version tv JOIN tool_definition td ON td.id = tv.tool_definition_id
        WHERE tv.tenant_id = ? ORDER BY td.tool_key, tv.semantic_version DESC LIMIT ?
        """,
        AgentPlatformStore::tool,
        TENANT,
        limit);
  }

  public Optional<ToolVersion> tool(String id) {
    return jdbc
        .query(
            """
            SELECT tv.*, td.tool_key, td.display_name, td.description
            FROM tool_version tv JOIN tool_definition td ON td.id = tv.tool_definition_id
            WHERE tv.tenant_id = ? AND tv.id = ?
            """,
            AgentPlatformStore::tool,
            TENANT,
            id)
        .stream()
        .findFirst();
  }

  public void insertGrant(ToolGrant value, String actorId) {
    jdbc.update(
        """
        INSERT INTO agent_tool_grant
          (id, tenant_id, agent_version_id, tool_version_id, operations_json,
           resource_selector_json, argument_constraints_json, approval_mode, expires_at,
           revision, created_by, created_at, revoked_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        value.id(),
        TENANT,
        value.agentVersionId(),
        value.toolVersionId(),
        value.operationsJson(),
        value.resourceSelectorJson(),
        value.argumentConstraintsJson(),
        value.approvalMode(),
        timestamp(value.expiresAt()),
        value.revision(),
        actorId,
        timestamp(value.createdAt()),
        timestamp(value.revokedAt()));
  }

  public List<ToolGrant> grants(int limit) {
    return jdbc.query(
        """
        SELECT * FROM agent_tool_grant WHERE tenant_id = ?
        ORDER BY created_at DESC LIMIT ?
        """,
        AgentPlatformStore::grant,
        TENANT,
        limit);
  }

  public List<ToolGrant> activeGrants(String agentVersionId, Instant now) {
    return jdbc.query(
        """
        SELECT * FROM agent_tool_grant
        WHERE tenant_id = ? AND agent_version_id = ? AND revoked_at IS NULL
          AND (expires_at IS NULL OR expires_at > ?)
        ORDER BY created_at
        """,
        AgentPlatformStore::grant,
        TENANT,
        agentVersionId,
        timestamp(now));
  }

  public boolean revokeGrant(String id, long expectedRevision, Instant now) {
    return jdbc.update(
            """
            UPDATE agent_tool_grant SET revoked_at = ?, revision = revision + 1
            WHERE tenant_id = ? AND id = ? AND revision = ? AND revoked_at IS NULL
            """,
            timestamp(now),
            TENANT,
            id,
            expectedRevision)
        == 1;
  }

  public void insertRun(AgentRun value) {
    jdbc.update(
        """
        INSERT INTO agent_run
          (id, tenant_id, agent_id, agent_version_id, principal_id, status, input_digest,
           resource_handles_json, budget_json, dependency_digest, current_sequence, revision,
           failure_code, created_at, updated_at, completed_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        value.id(),
        TENANT,
        value.agentId(),
        value.agentVersionId(),
        value.principalId(),
        value.status(),
        value.inputDigest(),
        value.resourceHandlesJson(),
        value.budgetJson(),
        value.dependencyDigest(),
        value.currentSequence(),
        value.revision(),
        value.failureCode(),
        timestamp(value.createdAt()),
        timestamp(value.updatedAt()),
        timestamp(value.completedAt()));
  }

  public Optional<AgentRun> run(String id, String actorId) {
    return jdbc
        .query(
            """
            SELECT r.*, v.version_number FROM agent_run r
            JOIN agent_version v ON v.id = r.agent_version_id
            WHERE r.tenant_id = ? AND r.id = ? AND r.principal_id = ?
            """,
            AgentPlatformStore::run,
            TENANT,
            id,
            actorId)
        .stream()
        .findFirst();
  }

  public List<AgentRun> runs(String actorId, String status, int limit) {
    if (status == null) {
      return jdbc.query(
          """
          SELECT r.*, v.version_number FROM agent_run r
          JOIN agent_version v ON v.id = r.agent_version_id
          WHERE r.tenant_id = ? AND r.principal_id = ?
          ORDER BY r.created_at DESC LIMIT ?
          """,
          AgentPlatformStore::run,
          TENANT,
          actorId,
          limit);
    }
    return jdbc.query(
        """
        SELECT r.*, v.version_number FROM agent_run r
        JOIN agent_version v ON v.id = r.agent_version_id
        WHERE r.tenant_id = ? AND r.principal_id = ? AND r.status = ?
        ORDER BY r.created_at DESC LIMIT ?
        """,
        AgentPlatformStore::run,
        TENANT,
        actorId,
        status,
        limit);
  }

  public boolean transitionRun(
      String id,
      long expectedRevision,
      String status,
      String failureCode,
      Instant completedAt,
      Instant now) {
    return jdbc.update(
            """
            UPDATE agent_run
            SET status = ?, failure_code = ?, completed_at = ?, updated_at = ?, revision = revision + 1
            WHERE tenant_id = ? AND id = ? AND revision = ?
            """,
            status,
            failureCode,
            timestamp(completedAt),
            timestamp(now),
            TENANT,
            id,
            expectedRevision)
        == 1;
  }

  public long appendRunEvent(
      String id, String eventId, String type, String payloadJson, Instant now) {
    Long sequence =
        jdbc.queryForObject(
            "SELECT current_sequence FROM agent_run WHERE tenant_id = ? AND id = ? FOR UPDATE",
            Long.class,
            TENANT,
            id);
    if (sequence == null) {
      throw new IllegalStateException("Agent run disappeared");
    }
    long next = sequence + 1;
    jdbc.update(
        "UPDATE agent_run SET current_sequence = ?, updated_at = ? WHERE tenant_id = ? AND id = ?",
        next,
        timestamp(now),
        TENANT,
        id);
    jdbc.update(
        """
        INSERT INTO agent_run_event
          (id, tenant_id, run_id, sequence_number, event_type, safe_payload_json, occurred_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        eventId,
        TENANT,
        id,
        next,
        type,
        payloadJson,
        timestamp(now));
    return next;
  }

  public List<AgentRunEvent> runEvents(String runId, long after, int limit) {
    return jdbc.query(
        """
        SELECT * FROM agent_run_event
        WHERE tenant_id = ? AND run_id = ? AND sequence_number > ?
        ORDER BY sequence_number LIMIT ?
        """,
        AgentPlatformStore::event,
        TENANT,
        runId,
        after,
        limit);
  }

  public Optional<AgentRunEvent> runEvent(String id, String runId) {
    return jdbc
        .query(
            "SELECT * FROM agent_run_event WHERE tenant_id = ? AND id = ? AND run_id = ?",
            AgentPlatformStore::event,
            TENANT,
            id,
            runId)
        .stream()
        .findFirst();
  }

  public boolean recordCommand(
      String id,
      String runId,
      String type,
      String idempotencyKey,
      long expectedRevision,
      String actorId,
      Instant now) {
    try {
      jdbc.update(
          """
          INSERT INTO agent_command
            (id, tenant_id, run_id, command_type, idempotency_key,
             expected_revision, requested_by, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """,
          id,
          TENANT,
          runId,
          type,
          idempotencyKey,
          expectedRevision,
          actorId,
          timestamp(now));
      return true;
    } catch (org.springframework.dao.DuplicateKeyException exception) {
      return false;
    }
  }

  public List<MemoryEntry> memories(String actorId, String purpose, String state, int limit) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT * FROM agent_memory_entry WHERE tenant_id = ? AND principal_id = ?");
    java.util.ArrayList<Object> args = new java.util.ArrayList<>(List.of(TENANT, actorId));
    if (purpose != null) {
      sql.append(" AND purpose = ?");
      args.add(purpose);
    }
    if (state != null) {
      sql.append(" AND state = ?");
      args.add(state);
    }
    sql.append(" ORDER BY created_at DESC LIMIT ?");
    args.add(limit);
    return jdbc.query(sql.toString(), AgentPlatformStore::memory, args.toArray());
  }

  public Optional<MemoryEntry> memory(String id, String actorId) {
    return jdbc
        .query(
            """
            SELECT * FROM agent_memory_entry
            WHERE tenant_id = ? AND id = ? AND principal_id = ?
            """,
            AgentPlatformStore::memory,
            TENANT,
            id,
            actorId)
        .stream()
        .findFirst();
  }

  public boolean updateMemoryState(
      String id, String actorId, long revision, String state, Instant deletedAt) {
    return jdbc.update(
            """
            UPDATE agent_memory_entry
            SET state = ?, deleted_at = ?, revision = revision + 1
            WHERE tenant_id = ? AND id = ? AND principal_id = ? AND revision = ?
            """,
            state,
            timestamp(deletedAt),
            TENANT,
            id,
            actorId,
            revision)
        == 1;
  }

  public void insertPurgeJob(String id, String memoryId, String idempotencyKey, Instant now) {
    jdbc.update(
        """
        INSERT INTO agent_memory_purge_job
          (id, tenant_id, memory_entry_id, idempotency_key, status, created_at)
        VALUES (?, ?, ?, ?, 'PENDING', ?)
        """,
        id,
        TENANT,
        memoryId,
        idempotencyKey,
        timestamp(now));
  }

  public void insertMcpServer(McpServer value) {
    jdbc.update(
        """
        INSERT INTO mcp_server
          (id, tenant_id, owner_id, name, transport, endpoint, auth_type, credential_ref,
           status, revision, created_at, updated_at, disabled_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        value.id(),
        TENANT,
        value.ownerId(),
        value.name(),
        value.transport(),
        value.endpoint(),
        value.authType(),
        value.credentialRef(),
        value.status(),
        value.revision(),
        timestamp(value.createdAt()),
        timestamp(value.updatedAt()),
        timestamp(value.disabledAt()));
  }

  public List<McpServer> mcpServers(String actorId, int limit) {
    return jdbc.query(
        """
        SELECT * FROM mcp_server WHERE tenant_id = ? AND owner_id = ?
        ORDER BY updated_at DESC LIMIT ?
        """,
        AgentPlatformStore::mcpServer,
        TENANT,
        actorId,
        limit);
  }

  public Optional<McpServer> mcpServer(String id, String actorId) {
    return jdbc
        .query(
            "SELECT * FROM mcp_server WHERE tenant_id = ? AND id = ? AND owner_id = ?",
            AgentPlatformStore::mcpServer,
            TENANT,
            id,
            actorId)
        .stream()
        .findFirst();
  }

  public boolean updateMcpServer(
      String id,
      String actorId,
      long revision,
      String name,
      String endpoint,
      String status,
      Instant disabledAt,
      Instant now) {
    return jdbc.update(
            """
            UPDATE mcp_server
            SET name = ?, endpoint = ?, status = ?, disabled_at = ?, updated_at = ?,
                revision = revision + 1
            WHERE tenant_id = ? AND id = ? AND owner_id = ? AND revision = ?
            """,
            name,
            endpoint,
            status,
            timestamp(disabledAt),
            timestamp(now),
            TENANT,
            id,
            actorId,
            revision)
        == 1;
  }

  public void replaceCapabilities(String serverId, List<McpCapability> values) {
    jdbc.update(
        "UPDATE mcp_tool_mapping SET status = 'SUSPENDED' WHERE tenant_id = ? "
            + "AND capability_id IN (SELECT id FROM mcp_capability WHERE tenant_id = ? AND server_id = ?)",
        TENANT,
        TENANT,
        serverId);
    jdbc.update(
        "UPDATE mcp_capability SET status = 'SUSPENDED' WHERE tenant_id = ? AND server_id = ?",
        TENANT,
        serverId);
    for (McpCapability value : values) {
      List<String> existing =
          jdbc.query(
              "SELECT id FROM mcp_capability WHERE tenant_id = ? AND server_id = ? AND external_name = ?",
              (rs, row) -> rs.getString(1),
              TENANT,
              serverId,
              value.externalName());
      if (existing.isEmpty()) {
        jdbc.update(
            """
            INSERT INTO mcp_capability
              (id, tenant_id, server_id, external_name, capability_type, schema_digest,
               schema_json, status, discovered_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            value.id(),
            TENANT,
            serverId,
            value.externalName(),
            value.type(),
            value.schemaDigest(),
            value.schemaJson(),
            value.status(),
            timestamp(value.discoveredAt()));
      } else {
        jdbc.update(
            """
            UPDATE mcp_capability
            SET capability_type = ?, schema_digest = ?, schema_json = ?, status = ?, discovered_at = ?
            WHERE tenant_id = ? AND id = ?
            """,
            value.type(),
            value.schemaDigest(),
            value.schemaJson(),
            value.status(),
            timestamp(value.discoveredAt()),
            TENANT,
            existing.getFirst());
      }
    }
  }

  public List<McpCapability> capabilities(String serverId) {
    return jdbc.query(
        """
        SELECT * FROM mcp_capability WHERE tenant_id = ? AND server_id = ?
        ORDER BY external_name
        """,
        AgentPlatformStore::mcpCapability,
        TENANT,
        serverId);
  }

  public Optional<McpCapability> capability(String id, String serverId) {
    return jdbc
        .query(
            """
            SELECT * FROM mcp_capability
            WHERE tenant_id = ? AND id = ? AND server_id = ?
            """,
            AgentPlatformStore::mcpCapability,
            TENANT,
            id,
            serverId)
        .stream()
        .findFirst();
  }

  public void insertMcpToolMapping(
      String id,
      String capabilityId,
      String toolVersionId,
      String digest,
      String actorId,
      Instant now) {
    jdbc.update(
        """
        INSERT INTO mcp_tool_mapping
          (id, tenant_id, capability_id, tool_version_id, capability_digest,
           status, created_by, created_at)
        VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
        """,
        id,
        TENANT,
        capabilityId,
        toolVersionId,
        digest,
        actorId,
        timestamp(now));
  }

  public void insertDataset(EvaluationDataset value) {
    jdbc.update(
        """
        INSERT INTO eval_dataset
          (id, tenant_id, owner_id, name, description, status, revision, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        value.id(),
        TENANT,
        value.ownerId(),
        value.name(),
        value.description(),
        value.status(),
        value.revision(),
        timestamp(value.createdAt()),
        timestamp(value.updatedAt()));
  }

  public List<EvaluationDataset> datasets(String actorId, int limit) {
    return jdbc.query(
        """
        SELECT * FROM eval_dataset
        WHERE tenant_id = ? AND (owner_id = ? OR owner_id = 'system')
        ORDER BY updated_at DESC LIMIT ?
        """,
        AgentPlatformStore::dataset,
        TENANT,
        actorId,
        limit);
  }

  public boolean datasetVersionExists(String id) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM eval_dataset_version WHERE tenant_id = ? AND id = ?",
            Integer.class,
            TENANT,
            id);
    return count != null && count > 0;
  }

  public List<EvaluationCase> evaluationCases(List<String> datasetVersionIds) {
    if (datasetVersionIds.isEmpty()) {
      return List.of();
    }
    String placeholders =
        String.join(",", java.util.Collections.nCopies(datasetVersionIds.size(), "?"));
    List<Object> args = new java.util.ArrayList<>();
    args.add(TENANT);
    args.addAll(datasetVersionIds);
    return jdbc.query(
        "SELECT case_key, agent_type, fixture_json, assertions_json, content_digest "
            + "FROM eval_case WHERE tenant_id = ? AND dataset_version_id IN ("
            + placeholders
            + ") ORDER BY agent_type, case_key",
        (rs, row) ->
            new EvaluationCase(
                rs.getString("case_key"),
                rs.getString("agent_type"),
                rs.getString("fixture_json"),
                rs.getString("assertions_json"),
                rs.getString("content_digest")),
        args.toArray());
  }

  public void insertDatasetVersion(
      String id, String datasetId, int version, String digest, int caseCount, Instant now) {
    jdbc.update(
        """
        INSERT INTO eval_dataset_version
          (id, tenant_id, dataset_id, version_number, content_digest, case_count, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        TENANT,
        datasetId,
        version,
        digest,
        caseCount,
        timestamp(now));
  }

  public void insertEvaluationCase(
      String id,
      String datasetVersionId,
      String caseKey,
      String agentType,
      String fixtureJson,
      String assertionsJson,
      String digest) {
    jdbc.update(
        """
        INSERT INTO eval_case
          (id, tenant_id, dataset_version_id, case_key, agent_type, fixture_json,
           assertions_json, content_digest)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        TENANT,
        datasetVersionId,
        caseKey,
        agentType,
        fixtureJson,
        assertionsJson,
        digest);
  }

  public void insertSuite(EvaluationSuite value) {
    jdbc.update(
        """
        INSERT INTO eval_suite
          (id, tenant_id, owner_id, name, status, revision, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        value.id(),
        TENANT,
        value.ownerId(),
        value.name(),
        value.status(),
        value.revision(),
        timestamp(value.createdAt()));
    jdbc.update(
        """
        INSERT INTO eval_suite_version
          (id, tenant_id, suite_id, version_number, dataset_versions_json, gate_policy_json,
           scorer_versions_json, content_digest, created_at)
        VALUES (?, ?, ?, 1, ?, ?, '[]', ?, ?)
        """,
        value.versionId(),
        TENANT,
        value.id(),
        value.datasetVersionsJson(),
        value.gatePolicyJson(),
        value.digest(),
        timestamp(value.createdAt()));
  }

  public List<EvaluationSuite> suites(String actorId, int limit) {
    return jdbc.query(
        """
        SELECT s.*, v.id AS version_id, v.dataset_versions_json, v.gate_policy_json,
               v.content_digest
        FROM eval_suite s JOIN eval_suite_version v ON v.suite_id = s.id AND v.version_number = 1
        WHERE s.tenant_id = ? AND (s.owner_id = ? OR s.owner_id = 'system')
        ORDER BY s.created_at DESC LIMIT ?
        """,
        AgentPlatformStore::suite,
        TENANT,
        actorId,
        limit);
  }

  public Optional<EvaluationSuite> suiteVersion(String versionId, String actorId) {
    return jdbc
        .query(
            """
            SELECT s.*, v.id AS version_id, v.dataset_versions_json, v.gate_policy_json,
                   v.content_digest
            FROM eval_suite s JOIN eval_suite_version v ON v.suite_id = s.id
            WHERE s.tenant_id = ? AND s.owner_id = ? AND v.id = ?
            """,
            AgentPlatformStore::suite,
            TENANT,
            actorId,
            versionId)
        .stream()
        .findFirst();
  }

  public void insertEvaluationRun(EvaluationRun value) {
    jdbc.update(
        """
        INSERT INTO eval_run
          (id, tenant_id, suite_version_id, candidate_agent_version_id,
           baseline_agent_version_id, repeat_count, status, gate_status,
           environment_digest, revision, created_by, created_at, completed_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        value.id(),
        TENANT,
        value.suiteVersionId(),
        value.candidateAgentVersionId(),
        value.baselineAgentVersionId(),
        value.repeatCount(),
        value.status(),
        value.gateStatus(),
        value.environmentDigest(),
        value.revision(),
        value.createdBy(),
        timestamp(value.createdAt()),
        timestamp(value.completedAt()));
  }

  public Optional<EvaluationRun> evaluationRun(String id, String actorId) {
    Optional<EvaluationRun> run =
        jdbc
            .query(
                "SELECT * FROM eval_run WHERE tenant_id = ? AND id = ? AND created_by = ?",
                AgentPlatformStore::evaluationRun,
                TENANT,
                id,
                actorId)
            .stream()
            .findFirst();
    return run.map(
        value ->
            new EvaluationRun(
                value.id(),
                value.suiteVersionId(),
                value.candidateAgentVersionId(),
                value.baselineAgentVersionId(),
                value.repeatCount(),
                value.status(),
                value.gateStatus(),
                value.environmentDigest(),
                value.revision(),
                value.createdBy(),
                value.createdAt(),
                value.completedAt(),
                metrics(id),
                gates(id)));
  }

  public boolean completeEvaluation(
      String id, long revision, String status, String gateStatus, Instant now) {
    return jdbc.update(
            """
            UPDATE eval_run SET status = ?, gate_status = ?, revision = revision + 1,
              completed_at = ? WHERE tenant_id = ? AND id = ? AND revision = ?
            """,
            status,
            gateStatus,
            timestamp(now),
            TENANT,
            id,
            revision)
        == 1;
  }

  public void insertMetric(String id, String runId, Metric value, Instant now) {
    jdbc.update(
        """
        INSERT INTO eval_metric
          (id, tenant_id, eval_run_id, metric_key, metric_value, sample_count,
           confidence_low, confidence_high, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        TENANT,
        runId,
        value.key(),
        value.value(),
        value.sampleCount(),
        value.low(),
        value.high(),
        timestamp(now));
  }

  public void insertGate(String id, String runId, Gate value, Instant now) {
    jdbc.update(
        """
        INSERT INTO eval_gate_result
          (id, tenant_id, eval_run_id, gate_key, status, actual_value,
           threshold_value, reason_code, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        TENANT,
        runId,
        value.key(),
        value.status(),
        value.actual(),
        value.threshold(),
        value.reasonCode(),
        timestamp(now));
  }

  private List<Metric> metrics(String runId) {
    return jdbc.query(
        """
        SELECT metric_key, metric_value, sample_count, confidence_low, confidence_high
        FROM eval_metric WHERE tenant_id = ? AND eval_run_id = ? ORDER BY metric_key
        """,
        (rs, row) ->
            new Metric(
                rs.getString("metric_key"),
                rs.getDouble("metric_value"),
                rs.getInt("sample_count"),
                nullableDouble(rs, "confidence_low"),
                nullableDouble(rs, "confidence_high")),
        TENANT,
        runId);
  }

  private List<Gate> gates(String runId) {
    return jdbc.query(
        """
        SELECT gate_key, status, actual_value, threshold_value, reason_code
        FROM eval_gate_result WHERE tenant_id = ? AND eval_run_id = ? ORDER BY gate_key
        """,
        (rs, row) ->
            new Gate(
                rs.getString("gate_key"),
                rs.getString("status"),
                nullableDouble(rs, "actual_value"),
                nullableDouble(rs, "threshold_value"),
                rs.getString("reason_code")),
        TENANT,
        runId);
  }

  private static AgentDefinition definition(ResultSet rs, int row) throws SQLException {
    return new AgentDefinition(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("owner_id"),
        rs.getString("name"),
        rs.getString("agent_type"),
        rs.getString("description"),
        rs.getString("status"),
        rs.getString("draft_json"),
        rs.getLong("draft_revision"),
        nullableInt(rs, "published_version"),
        rs.getLong("revision"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
        instant(rs, "archived_at"));
  }

  private static AgentVersion version(ResultSet rs, int row) throws SQLException {
    return new AgentVersion(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("agent_id"),
        nullableInt(rs, "version_number"),
        rs.getString("snapshot_status"),
        rs.getLong("source_draft_revision"),
        rs.getString("content_digest"),
        rs.getString("config_json"),
        rs.getString("evaluation_run_id"),
        rs.getString("created_by"),
        instant(rs, "created_at"),
        rs.getString("published_by"),
        instant(rs, "published_at"));
  }

  private static ToolVersion tool(ResultSet rs, int row) throws SQLException {
    return new ToolVersion(
        rs.getString("id"),
        rs.getString("tool_key"),
        rs.getString("display_name"),
        rs.getString("description"),
        rs.getString("semantic_version"),
        rs.getString("content_digest"),
        rs.getString("risk_class"),
        rs.getString("idempotency_mode"),
        rs.getString("operations_json"),
        rs.getString("input_schema_json"),
        rs.getString("output_schema_json"),
        rs.getInt("max_duration_ms"),
        rs.getInt("max_result_bytes"));
  }

  private static ToolGrant grant(ResultSet rs, int row) throws SQLException {
    return new ToolGrant(
        rs.getString("id"),
        rs.getString("agent_version_id"),
        rs.getString("tool_version_id"),
        rs.getString("operations_json"),
        rs.getString("resource_selector_json"),
        rs.getString("argument_constraints_json"),
        rs.getString("approval_mode"),
        instant(rs, "expires_at"),
        rs.getLong("revision"),
        instant(rs, "created_at"),
        instant(rs, "revoked_at"));
  }

  private static AgentRun run(ResultSet rs, int row) throws SQLException {
    return new AgentRun(
        rs.getString("id"),
        rs.getString("agent_id"),
        rs.getString("agent_version_id"),
        rs.getInt("version_number"),
        rs.getString("principal_id"),
        rs.getString("status"),
        rs.getString("input_digest"),
        rs.getString("resource_handles_json"),
        rs.getString("budget_json"),
        rs.getString("dependency_digest"),
        rs.getLong("current_sequence"),
        rs.getLong("revision"),
        rs.getString("failure_code"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
        instant(rs, "completed_at"));
  }

  private static AgentRunEvent event(ResultSet rs, int row) throws SQLException {
    return new AgentRunEvent(
        rs.getString("id"),
        rs.getString("run_id"),
        rs.getLong("sequence_number"),
        rs.getString("event_type"),
        rs.getString("safe_payload_json"),
        instant(rs, "occurred_at"));
  }

  private static MemoryEntry memory(ResultSet rs, int row) throws SQLException {
    return new MemoryEntry(
        rs.getString("id"),
        rs.getString("principal_id"),
        rs.getString("agent_id"),
        rs.getString("purpose"),
        rs.getString("source_type"),
        rs.getString("source_id"),
        rs.getString("sensitivity"),
        rs.getString("provenance_json"),
        rs.getString("content_digest"),
        rs.getString("vector_entry_id"),
        rs.getString("state"),
        instant(rs, "retention_deadline"),
        rs.getLong("revision"),
        instant(rs, "created_at"),
        instant(rs, "deleted_at"));
  }

  private static McpServer mcpServer(ResultSet rs, int row) throws SQLException {
    return new McpServer(
        rs.getString("id"),
        rs.getString("owner_id"),
        rs.getString("name"),
        rs.getString("transport"),
        rs.getString("endpoint"),
        rs.getString("auth_type"),
        rs.getString("credential_ref"),
        rs.getString("status"),
        rs.getLong("revision"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
        instant(rs, "disabled_at"));
  }

  private static McpCapability mcpCapability(ResultSet rs, int row) throws SQLException {
    return new McpCapability(
        rs.getString("id"),
        rs.getString("server_id"),
        rs.getString("external_name"),
        rs.getString("capability_type"),
        rs.getString("schema_digest"),
        rs.getString("schema_json"),
        rs.getString("status"),
        instant(rs, "discovered_at"));
  }

  private static EvaluationDataset dataset(ResultSet rs, int row) throws SQLException {
    return new EvaluationDataset(
        rs.getString("id"),
        rs.getString("owner_id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("status"),
        rs.getLong("revision"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private static EvaluationSuite suite(ResultSet rs, int row) throws SQLException {
    return new EvaluationSuite(
        rs.getString("id"),
        rs.getString("owner_id"),
        rs.getString("name"),
        rs.getString("status"),
        rs.getLong("revision"),
        instant(rs, "created_at"),
        rs.getString("version_id"),
        rs.getString("dataset_versions_json"),
        rs.getString("gate_policy_json"),
        rs.getString("content_digest"));
  }

  private static EvaluationRun evaluationRun(ResultSet rs, int row) throws SQLException {
    return new EvaluationRun(
        rs.getString("id"),
        rs.getString("suite_version_id"),
        rs.getString("candidate_agent_version_id"),
        rs.getString("baseline_agent_version_id"),
        rs.getInt("repeat_count"),
        rs.getString("status"),
        rs.getString("gate_status"),
        rs.getString("environment_digest"),
        rs.getLong("revision"),
        rs.getString("created_by"),
        instant(rs, "created_at"),
        instant(rs, "completed_at"),
        List.of(),
        List.of());
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(ResultSet rs, String name) throws SQLException {
    Timestamp value = rs.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }

  private static Integer nullableInt(ResultSet rs, String name) throws SQLException {
    int value = rs.getInt(name);
    return rs.wasNull() ? null : value;
  }

  private static Double nullableDouble(ResultSet rs, String name) throws SQLException {
    double value = rs.getDouble(name);
    return rs.wasNull() ? null : value;
  }
}
