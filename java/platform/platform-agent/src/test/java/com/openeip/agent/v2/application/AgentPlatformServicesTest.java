package com.openeip.agent.v2.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentRun;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationRun;
import com.openeip.agent.v2.infrastructure.AgentEvaluationGateway;
import com.openeip.agent.v2.infrastructure.AgentPlatformInitializer;
import com.openeip.agent.v2.infrastructure.AgentPlatformStore;
import com.openeip.agent.v2.infrastructure.AgentRuntimeV2Gateway;
import com.openeip.agent.v2.infrastructure.McpGatewayClient;
import com.openeip.agent.v2.shared.AgentPlatformException;
import com.openeip.agent.v2.support.AgentV2TestDatabase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AgentPlatformServicesTest {
  private static final String SECRET = "test-agent-capability-secret-at-least-32-characters";
  private static final String TOKEN = "test-internal-token";
  private final AtomicReference<String> runtimeStatus = new AtomicReference<>("SUCCEEDED");
  private ObjectMapper mapper;
  private JdbcTemplate jdbc;
  private AgentPlatformStore store;
  private HttpServer server;
  private String baseUrl;
  private String actor;

  @BeforeEach
  void setUp() throws Exception {
    var database = AgentV2TestDatabase.create();
    jdbc = database.jdbc();
    store = database.store();
    mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    new AgentPlatformInitializer(store, mapper).initialize();
    actor = UUID.randomUUID().toString();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v2/internal/agent-runs:execute", this::runtime);
    server.createContext("/api/v2/internal/mcp:discover", this::mcp);
    server.createContext("/api/v2/internal/evaluations:run", this::evaluation);
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void definitionToolAndMemoryGovernanceEnforceRevisionsAndBounds() {
    AgentDefinitionService definitions = new AgentDefinitionService(store, mapper);
    ToolGovernanceService tools = new ToolGovernanceService(store, mapper);
    MemoryGovernanceService memories = new MemoryGovernanceService(store);

    var custom = definitions.create(actor, "Support Agent", "custom", "answers tickets");
    assertThat(definitions.list(actor, 100)).hasSize(6);
    assertThat(definitions.get(actor, custom.id()).name()).isEqualTo("Support Agent");
    var patch =
        mapper
            .createObjectNode()
            .put("name", "Support Agent v2")
            .put("modelPolicy", "governed-model");
    var updated = definitions.update(actor, custom.id(), "\"0\"", patch);
    assertThat(updated.revision()).isOne();
    assertThat(updated.draftJson()).contains("governed-model");
    assertThatThrownBy(() -> definitions.update(actor, custom.id(), "0", patch))
        .isInstanceOf(AgentPlatformException.class);
    assertThatThrownBy(() -> definitions.create(actor, "Support Agent v2", "CUSTOM", "duplicate"))
        .isInstanceOf(AgentPlatformException.class);
    var archived = definitions.archive(actor, custom.id(), "1", "archive-support-agent-0001");
    assertThat(archived.status()).isEqualTo("ARCHIVED");

    var search = store.definitionByName("OpenEIP Search Agent").orElseThrow();
    var bi = store.definitionByName("OpenEIP BI Agent").orElseThrow();
    var biVersion = store.version(bi.id(), 1).orElseThrow();
    var searchTool =
        store.tools(100).stream()
            .filter(value -> value.toolKey().equals("openeip.knowledge.search"))
            .findFirst()
            .orElseThrow();
    var grant =
        tools.createGrant(
            actor,
            "create-tool-grant-0001",
            biVersion.id(),
            searchTool.id(),
            mapper.createArrayNode().add("search"),
            mapper.createObjectNode(),
            mapper.createObjectNode(),
            "NONE",
            null);
    assertThat(tools.tools(100)).isNotEmpty();
    assertThat(tools.grants(100)).extracting(value -> value.id()).contains(grant.id());
    assertThat(tools.revoke(actor, grant.id(), "0", "revoke-tool-grant-0001").revokedAt())
        .isNotNull();
    tools.validateIdempotency(actor, "validate-command-key-0001");

    String memoryId = insertMemory(search.id(), "ACTIVE");
    assertThat(memories.list(actor, "support", "ACTIVE", 10)).hasSize(1);
    assertThat(memories.get(actor, memoryId).contentDigest()).hasSize(64);
    var quarantined = memories.quarantine(actor, memoryId, "0", "quarantine-memory-0001");
    assertThat(quarantined.state()).isEqualTo("QUARANTINED");
    var deleting = memories.delete(actor, memoryId, "1", "delete-memory-entry-0001");
    assertThat(deleting.state()).isEqualTo("DELETING");
    assertThat(memories.delete(actor, memoryId, "2", "delete-memory-entry-0001").state())
        .isEqualTo("DELETING");
    assertThat(memories.exportMetadata(actor, "support", false, 100))
        .containsEntry("contentIncluded", false);
    assertThatThrownBy(() -> memories.exportMetadata(actor, null, true, 10))
        .isInstanceOf(AgentPlatformException.class);
  }

  @Test
  void runtimeCommandsCapabilitiesAndApprovalsUseDurableState() {
    AgentDefinitionService definitions = new AgentDefinitionService(store, mapper);
    AgentCapabilityService capability =
        new AgentCapabilityService(mapper, SECRET, 300, java.time.Clock.systemUTC());
    AgentRunService runs =
        new AgentRunService(
            store,
            definitions,
            capability,
            new AgentRuntimeV2Gateway(mapper, baseUrl, TOKEN),
            mapper);
    var search = store.definitionByName("OpenEIP Search Agent").orElseThrow();
    var succeeded =
        runs.create(
            actor,
            search.id(),
            "create-agent-run-0001",
            1,
            "Find the retention policy",
            mapper.createArrayNode(),
            mapper.createObjectNode().put("maxSteps", 8));
    assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
    assertThat(runs.list(actor, "SUCCEEDED", 10)).contains(succeeded);
    assertThat(runs.events(actor, succeeded.id(), 0, 100)).isNotEmpty();
    assertThat(
            runs.command(
                    actor,
                    succeeded.id(),
                    "CANCEL",
                    Long.toString(succeeded.revision()),
                    "cancel-terminal-run-0001")
                .status())
        .isEqualTo("SUCCEEDED");

    runtimeStatus.set("FAILED");
    var failed =
        runs.create(
            actor,
            search.id(),
            "create-agent-run-0002",
            1,
            "Fail safely",
            mapper.createArrayNode(),
            null);
    assertThat(failed.status()).isEqualTo("FAILED");
    assertThat(failed.failureCode()).isEqualTo("AGENT2-S-001");
    assertThat(
            runs.retryStep(
                    actor,
                    failed.id(),
                    UUID.randomUUID().toString(),
                    Long.toString(failed.revision()),
                    "retry-agent-step-0001")
                .status())
        .isEqualTo("FAILED");

    AgentRun controllable = insertRun(search.id(), "QUEUED", 0);
    var paused = runs.command(actor, controllable.id(), "PAUSE", "0", "pause-agent-run-0001");
    assertThat(paused.status()).isEqualTo("PAUSED");
    var resumed = runs.command(actor, paused.id(), "RESUME", "1", "resume-agent-run-0001");
    assertThat(resumed.status()).isEqualTo("QUEUED");
    var cancelled = runs.command(actor, resumed.id(), "CANCEL", "2", "cancel-agent-run-0001");
    assertThat(cancelled.status()).isEqualTo("CANCELLED");

    AgentRun approvalRun = insertRun(search.id(), "PAUSED", 0);
    String approvalId = UUID.randomUUID().toString();
    store.appendRunEvent(
        approvalRun.id(),
        approvalId,
        "tool.approval.required",
        "{\"toolId\":\"safe\"}",
        Instant.now());
    var approved =
        runs.decideApproval(
            actor,
            approvalRun.id(),
            approvalId,
            "APPROVE",
            "allowed by policy",
            "0",
            "approve-tool-call-0001");
    assertThat(approved.status()).isEqualTo("QUEUED");
  }

  @Test
  void mcpAndEvaluationGatewaysExecuteGovernedFlows() {
    McpGovernanceService mcp =
        new McpGovernanceService(store, new McpGatewayClient(mapper, baseUrl, TOKEN));
    var registered =
        mcp.create(
            actor,
            "create-mcp-server-0001",
            "Fixture",
            "STDIO",
            "managed://fixture/agent-v0.6",
            "NONE",
            null);
    assertThat(mcp.list(actor, 10))
        .extracting(value -> value.id())
        .containsExactly(registered.id());
    assertThat(mcp.get(actor, registered.id()).name()).isEqualTo(registered.name());
    assertThat(mcp.test(actor, registered.id(), "0", "test-mcp-server-0001"))
        .containsEntry("policyStatus", "PASS");
    var capabilities = mcp.discover(actor, registered.id(), "0", "discover-mcp-server-0001");
    assertThat(capabilities).hasSize(1);
    var tool = store.tools(100).getFirst();
    assertThat(
            mcp.mapTool(
                actor,
                registered.id(),
                capabilities.getFirst().id(),
                tool.id(),
                "0",
                "map-mcp-tool-0001"))
        .containsEntry("status", "ACTIVE");
    var changed = mcp.update(actor, registered.id(), "0", "Fixture v2", null);
    assertThat(changed.revision()).isOne();
    assertThat(mcp.disable(actor, registered.id(), "1", "disable-mcp-server-0001").status())
        .isEqualTo("DISABLED");

    AgentEvaluationService evaluations =
        new AgentEvaluationService(
            store, new AgentEvaluationGateway(mapper, baseUrl, TOKEN), mapper);
    var dataset =
        evaluations.createDataset(actor, "create-eval-dataset-0001", "Regression", "draft");
    assertThat(evaluations.datasets(actor, 10))
        .extracting(value -> value.id())
        .contains(dataset.id());
    String datasetVersionId =
        jdbc.queryForObject("SELECT id FROM eval_dataset_version LIMIT 1", String.class);
    var suite =
        evaluations.createSuite(
            actor,
            "create-eval-suite-0001",
            "Safety suite",
            mapper.createArrayNode().add(datasetVersionId),
            mapper.createObjectNode().put("deterministicSafety", 1.0));
    assertThat(evaluations.suites(actor, 10)).extracting(value -> value.id()).contains(suite.id());
    var version =
        store
            .versions(store.definitionByName("OpenEIP Search Agent").orElseThrow().id())
            .getFirst();
    var result =
        evaluations.run(
            actor, "create-eval-run-0001", suite.versionId(), version.id(), version.id(), 1);
    assertThat(result.gateStatus()).isEqualTo("PASS");
    assertThat(evaluations.get(actor, result.id()).metrics()).isNotEmpty();

    AgentDefinitionService definitions = new AgentDefinitionService(store, mapper);
    var custom = definitions.create(actor, "Evaluated Support Agent", "CUSTOM", "support");
    var candidate =
        definitions.createCandidate(actor, custom.id(), "0", "create-agent-candidate-0001");
    assertThat(candidate.status()).isEqualTo("CANDIDATE");
    assertThat(candidate.version()).isNull();
    var candidateResult =
        evaluations.run(
            actor,
            "create-eval-run-candidate-0001",
            suite.versionId(),
            candidate.id(),
            version.id(),
            1);
    var published =
        definitions.publish(
            actor, custom.id(), "0", "publish-agent-candidate-0001", candidateResult.id());
    assertThat(published.id()).isEqualTo(candidate.id());
    assertThat(published.status()).isEqualTo("PUBLISHED");
    assertThat(published.version()).isOne();

    String runningId = UUID.randomUUID().toString();
    store.insertEvaluationRun(
        new EvaluationRun(
            runningId,
            suite.versionId(),
            version.id(),
            version.id(),
            1,
            "RUNNING",
            null,
            "f".repeat(64),
            0,
            actor,
            Instant.now(),
            null,
            List.of(),
            List.of()));
    assertThat(evaluations.cancel(actor, runningId, "0", "cancel-eval-run-0001").status())
        .isEqualTo("CANCELLED");
  }

  @Test
  void supportRejectsMalformedInputs() {
    assertThat(AgentPlatformSupport.revision("W/\"12\"")).isEqualTo(12);
    assertThat(AgentPlatformSupport.limit(100)).isEqualTo(100);
    assertThat(AgentPlatformSupport.instant("2026-08-30T00:00:00Z", "time")).isNotNull();
    assertThatThrownBy(() -> AgentPlatformSupport.uuid("not-a-uuid"))
        .isInstanceOf(AgentPlatformException.class);
    assertThatThrownBy(() -> AgentPlatformSupport.idempotencyKey("short"))
        .isInstanceOf(AgentPlatformException.class);
    assertThatThrownBy(() -> AgentPlatformSupport.limit(0))
        .isInstanceOf(AgentPlatformException.class);
    assertThatThrownBy(() -> AgentPlatformSupport.instant("tomorrow", "time"))
        .isInstanceOf(AgentPlatformException.class);
  }

  private AgentRun insertRun(String agentId, String status, long revision) {
    var version = store.version(agentId, 1).orElseThrow();
    AgentRun run =
        new AgentRun(
            UUID.randomUUID().toString(),
            agentId,
            version.id(),
            1,
            actor,
            status,
            "a".repeat(64),
            "[]",
            "{\"maxSteps\":8}",
            "b".repeat(64),
            0,
            revision,
            "FAILED".equals(status) ? "AGENT2-S-001" : null,
            Instant.now(),
            Instant.now(),
            null);
    store.insertRun(run);
    return run;
  }

  private String insertMemory(String agentId, String state) {
    String policyId = UUID.randomUUID().toString();
    String memoryId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO agent_memory_policy "
            + "(id, tenant_id, name, purpose, retention_seconds, max_items, max_bytes, revision, created_at) "
            + "VALUES (?, 'default', ?, 'support', 3600, 100, 1024, 0, ?)",
        policyId,
        "policy-" + policyId,
        java.sql.Timestamp.from(Instant.now()));
    jdbc.update(
        "INSERT INTO agent_memory_entry "
            + "(id, tenant_id, principal_id, agent_id, policy_id, purpose, source_type, source_id, "
            + "sensitivity, provenance_json, content_digest, state, retention_deadline, revision, created_at) "
            + "VALUES (?, 'default', ?, ?, ?, 'support', 'RUN', 'run-1', 'INTERNAL', '{}', ?, ?, ?, 0, ?)",
        memoryId,
        actor,
        agentId,
        policyId,
        "c".repeat(64),
        state,
        java.sql.Timestamp.from(Instant.now().plusSeconds(3600)),
        java.sql.Timestamp.from(Instant.now()));
    return memoryId;
  }

  private void runtime(HttpExchange exchange) throws java.io.IOException {
    exchange.getRequestBody().readAllBytes();
    String status = runtimeStatus.get();
    String body =
        "SUCCEEDED".equals(status)
            ? "{\"status\":\"SUCCEEDED\",\"failureCode\":null,\"events\":["
                + "{\"type\":\"plan.created\",\"payload\":{\"stepCount\":1}}]}"
            : "{\"status\":\"FAILED\",\"failureCode\":\"AGENT2-S-001\",\"events\":["
                + "{\"type\":\"run.failed\",\"payload\":{\"code\":\"AGENT2-S-001\"}}]}";
    respond(exchange, body);
  }

  private void mcp(HttpExchange exchange) throws java.io.IOException {
    exchange.getRequestBody().readAllBytes();
    respond(
        exchange,
        "{\"policyStatus\":\"PASS\",\"capabilities\":[{\"name\":\"fixture.echo\",\"type\":\"TOOL\",\"digest\":\""
            + "d".repeat(64)
            + "\",\"schema\":{\"type\":\"object\"}}]}");
  }

  private void evaluation(HttpExchange exchange) throws java.io.IOException {
    exchange.getRequestBody().readAllBytes();
    respond(
        exchange,
        "{\"metrics\":[{\"key\":\"deterministicSafety\",\"value\":1.0,"
            + "\"sampleCount\":500,\"low\":1.0,\"high\":1.0}],\"gates\":[{"
            + "\"key\":\"deterministicSafety\",\"status\":\"PASS\",\"actual\":1.0,"
            + "\"threshold\":1.0,\"reasonCode\":\"THRESHOLD_MET\"}]}");
  }

  private static void respond(HttpExchange exchange, String body) throws java.io.IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
