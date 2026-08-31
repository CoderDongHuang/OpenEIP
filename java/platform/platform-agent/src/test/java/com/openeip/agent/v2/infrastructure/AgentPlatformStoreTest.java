package com.openeip.agent.v2.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentRun;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationRun;
import com.openeip.agent.v2.domain.AgentPlatformModels.Gate;
import com.openeip.agent.v2.domain.AgentPlatformModels.McpCapability;
import com.openeip.agent.v2.domain.AgentPlatformModels.McpServer;
import com.openeip.agent.v2.domain.AgentPlatformModels.Metric;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class AgentPlatformStoreTest {
  private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
  private JdbcTemplate jdbc;
  private AgentPlatformStore store;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:agentv2-"
            + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(dataSource);
    String migration =
        new ClassPathResource("db/migration/V2.6.0__init_agent_platform_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    try (var connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection,
          new ByteArrayResource(h2Compatible(migration).getBytes(StandardCharsets.UTF_8)));
    }
    store = new AgentPlatformStore(jdbc);
  }

  @Test
  void initializesAndExercisesDurableAgentPlatformStore() {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    AgentPlatformInitializer initializer = new AgentPlatformInitializer(store, mapper);
    initializer.initialize();
    initializer.initialize();

    String actor = UUID.randomUUID().toString();
    assertThat(store.listDefinitions(actor, 100)).hasSize(5);
    var definition = store.definitionByName("OpenEIP Search Agent").orElseThrow();
    assertThat(store.definition(definition.id(), actor)).isPresent();
    assertThat(store.definition("missing", actor)).isEmpty();
    assertThat(store.versions(definition.id())).hasSize(1);
    var version = store.version(definition.id(), 1).orElseThrow();
    assertThat(store.versionById(version.id())).contains(version);
    assertThat(
            store.updateDefinition(
                definition.id(),
                definition.revision(),
                definition.name(),
                "updated",
                definition.draftJson(),
                definition.status(),
                definition.publishedVersion(),
                null,
                NOW))
        .isTrue();
    assertThat(
            store.updateDefinition(
                definition.id(),
                definition.revision(),
                definition.name(),
                "stale",
                definition.draftJson(),
                definition.status(),
                definition.publishedVersion(),
                null,
                NOW))
        .isFalse();

    assertThat(store.tools(100)).hasSize(6);
    var tool =
        store.tools(100).stream()
            .filter(value -> value.toolKey().equals("openeip.knowledge.search"))
            .findFirst()
            .orElseThrow();
    assertThat(store.tool(tool.id())).contains(tool);
    assertThat(store.toolDefinitionId(tool.toolKey())).isPresent();
    var grant = store.activeGrants(version.id(), NOW).getFirst();
    assertThat(store.grants(100)).isNotEmpty();
    assertThat(store.revokeGrant(grant.id(), 0, NOW)).isTrue();
    assertThat(store.revokeGrant(grant.id(), 0, NOW)).isFalse();

    String runId = UUID.randomUUID().toString();
    AgentRun run =
        new AgentRun(
            runId,
            definition.id(),
            version.id(),
            1,
            actor,
            "QUEUED",
            "a".repeat(64),
            "[]",
            "{\"maxSteps\":8}",
            "b".repeat(64),
            0,
            0,
            null,
            NOW,
            NOW,
            null);
    store.insertRun(run);
    assertThat(store.run(runId, actor)).isPresent();
    assertThat(store.runs(actor, null, 10)).hasSize(1);
    assertThat(store.runs(actor, "QUEUED", 10)).hasSize(1);
    assertThat(store.runs(actor, "FAILED", 10)).isEmpty();
    String eventId = UUID.randomUUID().toString();
    assertThat(store.appendRunEvent(runId, eventId, "run.queued", "{}", NOW)).isOne();
    assertThat(store.runEvents(runId, 0, 10)).hasSize(1);
    assertThat(store.runEvent(eventId, runId)).isPresent();
    assertThat(store.transitionRun(runId, 0, "PLANNING", null, null, NOW)).isTrue();
    assertThat(store.transitionRun(runId, 0, "FAILED", "AGENT2-S-001", NOW, NOW)).isFalse();
    String commandId = UUID.randomUUID().toString();
    assertThat(store.recordCommand(commandId, runId, "CANCEL", "command-key-0001", 1, actor, NOW))
        .isTrue();
    assertThat(
            store.recordCommand(
                UUID.randomUUID().toString(), runId, "CANCEL", "command-key-0001", 1, actor, NOW))
        .isFalse();

    exerciseMemory(actor, definition.id());
    exerciseMcp(actor, tool.id());
    exerciseEvaluation(actor, version.id());
  }

  private void exerciseMemory(String actor, String agentId) {
    String policyId = UUID.randomUUID().toString();
    String memoryId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO agent_memory_policy "
            + "(id, tenant_id, name, purpose, retention_seconds, max_items, max_bytes, revision, created_at) "
            + "VALUES (?, 'default', 'session', 'support', 3600, 100, 1024, 0, ?)",
        policyId,
        java.sql.Timestamp.from(NOW));
    jdbc.update(
        "INSERT INTO agent_memory_entry "
            + "(id, tenant_id, principal_id, agent_id, policy_id, purpose, source_type, source_id, "
            + "sensitivity, provenance_json, content_digest, state, retention_deadline, revision, created_at) "
            + "VALUES (?, 'default', ?, ?, ?, 'support', 'RUN', 'run-1', 'INTERNAL', '{}', ?, "
            + "'ACTIVE', ?, 0, ?)",
        memoryId,
        actor,
        agentId,
        policyId,
        "c".repeat(64),
        java.sql.Timestamp.from(NOW.plusSeconds(3600)),
        java.sql.Timestamp.from(NOW));
    assertThat(store.memories(actor, null, null, 10)).hasSize(1);
    assertThat(store.memories(actor, "support", "ACTIVE", 10)).hasSize(1);
    assertThat(store.memory(memoryId, actor)).isPresent();
    assertThat(store.updateMemoryState(memoryId, actor, 0, "QUARANTINED", null)).isTrue();
    assertThat(store.updateMemoryState(memoryId, actor, 0, "DELETED", NOW)).isFalse();
    store.insertPurgeJob(UUID.randomUUID().toString(), memoryId, "purge-key-0000001", NOW);
  }

  private void exerciseMcp(String actor, String toolVersionId) {
    String serverId = UUID.randomUUID().toString();
    McpServer server =
        new McpServer(
            serverId,
            actor,
            "fixture",
            "STDIO",
            "managed://fixture/agent-v0.6",
            "NONE",
            null,
            "REGISTERED",
            0,
            NOW,
            NOW,
            null);
    store.insertMcpServer(server);
    assertThat(store.mcpServers(actor, 10)).containsExactly(server);
    assertThat(store.mcpServer(serverId, actor)).contains(server);
    assertThat(
            store.updateMcpServer(
                serverId, actor, 0, "fixture-2", server.endpoint(), "REGISTERED", null, NOW))
        .isTrue();
    assertThat(
            store.updateMcpServer(
                serverId, actor, 0, "stale", server.endpoint(), "REGISTERED", null, NOW))
        .isFalse();
    McpCapability capability =
        new McpCapability(
            UUID.randomUUID().toString(),
            serverId,
            "fixture.echo",
            "TOOL",
            "d".repeat(64),
            "{\"type\":\"object\"}",
            "QUARANTINED",
            NOW);
    store.replaceCapabilities(serverId, List.of(capability));
    assertThat(store.capabilities(serverId)).containsExactly(capability);
    assertThat(store.capability(capability.id(), serverId)).contains(capability);
    store.insertMcpToolMapping(
        UUID.randomUUID().toString(),
        capability.id(),
        toolVersionId,
        capability.schemaDigest(),
        actor,
        NOW);
    store.replaceCapabilities(serverId, List.of());
    assertThat(store.capabilities(serverId).getFirst().status()).isEqualTo("SUSPENDED");
  }

  private void exerciseEvaluation(String actor, String versionId) {
    var systemSuite = store.suites("system", 10).getFirst();
    assertThat(store.suiteVersion(systemSuite.versionId(), "system")).isPresent();
    List<String> datasets =
        List.of(new ObjectMapper().createArrayNode().add("unused").get(0).asText());
    assertThat(store.evaluationCases(List.of())).isEmpty();
    String datasetVersionId =
        jdbc.queryForObject("SELECT id FROM eval_dataset_version LIMIT 1", String.class);
    assertThat(store.datasetVersionExists(datasetVersionId)).isTrue();
    assertThat(store.evaluationCases(List.of(datasetVersionId))).hasSize(500);
    assertThat(store.datasets("system", 10)).hasSize(1);

    String runId = UUID.randomUUID().toString();
    EvaluationRun run =
        new EvaluationRun(
            runId,
            systemSuite.versionId(),
            versionId,
            versionId,
            1,
            "RUNNING",
            null,
            "e".repeat(64),
            0,
            actor,
            NOW,
            null,
            List.of(),
            List.of());
    store.insertEvaluationRun(run);
    store.insertMetric(
        UUID.randomUUID().toString(),
        runId,
        new Metric("deterministicSafety", 1.0, 500, 1.0, 1.0),
        NOW);
    store.insertGate(
        UUID.randomUUID().toString(),
        runId,
        new Gate("deterministicSafety", "PASS", 1.0, 1.0, "THRESHOLD_MET"),
        NOW);
    assertThat(store.evaluationRun(runId, actor).orElseThrow().metrics()).hasSize(1);
    assertThat(store.completeEvaluation(runId, 0, "COMPLETED", "PASS", NOW)).isTrue();
    assertThat(store.completeEvaluation(runId, 0, "CANCELLED", "FAIL", NOW)).isFalse();
    assertThat(datasets).containsExactly("unused");
  }

  private static String h2Compatible(String mysql) {
    String transformed =
        mysql
            .replaceAll("(?m)^\\s*KEY [^\\r\\n]+,?\\r?\\n", "")
            .replaceAll("UNIQUE KEY ([A-Za-z0-9_]+) \\(", "CONSTRAINT $1 UNIQUE (")
            .replaceAll(
                "\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;", ");");
    return transformed.replaceAll(",\\s*\\);", "\n);");
  }
}
