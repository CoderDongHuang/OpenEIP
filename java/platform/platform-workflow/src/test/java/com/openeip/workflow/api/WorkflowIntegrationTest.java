package com.openeip.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.WorkflowTestApplication;
import com.openeip.workflow.application.WorkflowTriggerService;
import com.openeip.workflow.domain.WorkflowRole;
import com.openeip.workflow.domain.entity.WorkflowMember;
import com.openeip.workflow.domain.repository.WorkflowMemberRepository;
import com.openeip.workflow.shared.WorkflowException;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = WorkflowTestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:workflow_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "openeip.workflow.scheduler-enabled=false"
    })
@AutoConfigureMockMvc
class WorkflowIntegrationTest {
  private static final String OWNER = "11111111-1111-1111-1111-111111111111";
  private static final String APPROVER = "22222222-2222-4222-8222-222222222222";
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper mapper;
  @Autowired WorkflowTriggerService triggerService;
  @Autowired WorkflowMemberRepository members;

  @Test
  void definitionPublishAndExecutionLifecycleIsDurableAndIdempotent() throws Exception {
    String workflowId = create("Fulfillment");
    mockMvc
        .perform(get("/api/v1/workflows").with(user(OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));
    mockMvc
        .perform(post("/api/v1/workflows/{id}/validate", workflowId).with(user(OWNER)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.valid").value(true));
    publish(workflowId, 0);

    String first = execute(workflowId, "manual-1");
    String second = execute(workflowId, "manual-1");
    assertThat(second).isEqualTo(first);
    mockMvc
        .perform(get("/api/v1/workflow-executions/{id}", first).with(user(OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    mockMvc
        .perform(get("/api/v1/workflow-executions/{id}/events", first).with(user(OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(7));
  }

  @Test
  void approvalAndWebhookResumeTheSameExecutionStateMachine() throws Exception {
    String workflowId = create("Approval flow");
    JsonNode workflow =
        mapper.readTree(
            mockMvc
                .perform(get("/api/v1/workflows/{id}", workflowId).with(user(OWNER)))
                .andReturn()
                .getResponse()
                .getContentAsString());
    String update =
        mapper.writeValueAsString(
            mapper
                .createObjectNode()
                .put("name", "Approval flow")
                .put("description", "Controlled")
                .set("graph", mapper.readTree(approvalGraph())));
    mockMvc
        .perform(
            patch("/api/v1/workflows/{id}", workflowId)
                .with(user(OWNER))
                .with(csrf())
                .header("If-Match", workflow.at("/data/draftRevision").asLong())
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.draftRevision").value(1));
    publish(workflowId, 1);

    String executionId = execute(workflowId, "approval-1");
    String eventBody =
        mockMvc
            .perform(get("/api/v1/workflow-executions/{id}/events", executionId).with(user(OWNER)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode approvalEvent = mapper.readTree(eventBody).path("data").findValue("approvalId");
    assertThat(approvalEvent).isNotNull();
    mockMvc
        .perform(
            post("/api/v1/workflow-approvals/{id}/decisions", approvalEvent.asText())
                .with(user(OWNER))
                .with(csrf())
                .header("Idempotency-Key", "decision-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"comment\":\"ok\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

    String triggerBody =
        mockMvc
            .perform(
                post("/api/v1/workflows/{id}/triggers", workflowId)
                    .with(user(OWNER))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"WEBHOOK\",\"enabled\":true,\"config\":{}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.secret").isString())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode trigger = mapper.readTree(triggerBody).path("data");
    mockMvc
        .perform(
            post("/api/v1/workflow-hooks/{id}", trigger.path("id").asText())
                .with(user(OWNER))
                .with(csrf())
                .header("X-Workflow-Hook-Secret", trigger.path("secret").asText())
                .header("Idempotency-Key", "hook-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.status").value("WAITING_APPROVAL"));
  }

  @Test
  void rejectsAnonymousStaleInvalidAndBadSecretRequests() throws Exception {
    mockMvc.perform(get("/api/v1/workflows")).andExpect(status().isUnauthorized());
    String workflowId = create("Validation");
    mockMvc
        .perform(
            patch("/api/v1/workflows/{id}", workflowId)
                .with(user(OWNER))
                .with(csrf())
                .header("If-Match", 9)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Validation\",\"description\":\"\",\"graph\":{}}"))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/api/v1/workflows/{id}/executions", workflowId)
                .with(user(OWNER))
                .with(csrf())
                .header("Idempotency-Key", "before-publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"input\":{}}"))
        .andExpect(status().isConflict());
    publish(workflowId, 0);
    mockMvc
        .perform(
            post("/api/v1/workflows/{id}/triggers", workflowId)
                .with(user(OWNER))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"WEBHOOK\",\"enabled\":true,\"config\":{\"secret\":\"must-not-persist\"}}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/workflows/{id}/triggers", workflowId)
                .with(user(OWNER))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"CRON\",\"enabled\":true,\"config\":{\"expression\":\"0 0 0 * * *\"}}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectionRetryEventTriggerCancellationAndVersionRestoreRemainConsistent() throws Exception {
    String workflowId = create("Recovery flow");
    updateGraph(workflowId, 0, approvalGraph());
    publish(workflowId, 1);
    mockMvc
        .perform(get("/api/v1/workflows/{id}/versions", workflowId).with(user(OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].version").value(1));

    String executionId = execute(workflowId, "reject-1");
    String approvalId = approvalId(executionId);
    mockMvc
        .perform(
            post("/api/v1/workflow-approvals/{id}/decisions", approvalId)
                .with(user(OWNER))
                .with(csrf())
                .header("Idempotency-Key", "reject-decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\",\"comment\":\"needs revision\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("FAILED"));
    mockMvc
        .perform(
            post("/api/v1/workflow-executions/{id}/nodes/approve/retry", executionId)
                .with(user(OWNER))
                .with(csrf())
                .header("Idempotency-Key", "retry-1"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.status").value("WAITING_APPROVAL"));
    mockMvc
        .perform(
            post("/api/v1/workflow-approvals/{id}/decisions", approvalId)
                .with(user(OWNER))
                .with(csrf())
                .header("Idempotency-Key", "approve-retry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

    mockMvc
        .perform(
            post("/api/v1/workflows/{id}/triggers", workflowId)
                .with(user(OWNER))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"EVENT\",\"enabled\":true,\"config\":{\"eventType\":\"order.created\"}}"))
        .andExpect(status().isCreated());
    String eventId = UUID.randomUUID().toString();
    assertThat(
            triggerService.processEvent(
                eventId, "order.created", "a".repeat(64), mapper.createObjectNode()))
        .isEqualTo(1);
    assertThat(
            triggerService.processEvent(
                eventId, "order.created", "a".repeat(64), mapper.createObjectNode()))
        .isZero();
    assertThatThrownBy(
            () ->
                triggerService.processEvent(
                    eventId, "order.created", "b".repeat(64), mapper.createObjectNode()))
        .isInstanceOf(WorkflowException.class);

    mockMvc
        .perform(
            post("/api/v1/workflows/{id}/versions/1/restore", workflowId)
                .with(user(OWNER))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.draftRevision").value(2));
  }

  @Test
  void conditionActivatesOnlyTheSelectedBranch() throws Exception {
    String workflowId = create("Conditional flow");
    updateGraph(workflowId, 0, conditionGraph());
    publish(workflowId, 1);

    String executionId = executeInput(workflowId, "condition-1", "{\"status\":\"ready\"}");
    String body =
        mockMvc
            .perform(get("/api/v1/workflow-executions/{id}/events", executionId).with(user(OWNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.nodeId == 'end_true')]").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Iterable<JsonNode> nodeIds = mapper.readTree(body).path("data").findValues("nodeId");
    assertThat(StreamSupport.stream(nodeIds.spliterator(), false).map(JsonNode::asText))
        .doesNotContain("end_false");
  }

  @Test
  void allApprovalWaitsForEveryExplicitAssignee() throws Exception {
    String workflowId = create("All approval flow");
    members.save(
        new WorkflowMember(
            UUID.randomUUID().toString(),
            "default",
            workflowId,
            APPROVER,
            WorkflowRole.APPROVER,
            java.time.Instant.now()));
    updateGraph(workflowId, 0, allApprovalGraph());
    publish(workflowId, 1);
    String executionId = execute(workflowId, "all-approval-1");
    String approvalId = approvalId(executionId);

    decide(approvalId, OWNER, "owner-decision", "WAITING_APPROVAL");
    decide(approvalId, APPROVER, "approver-decision", "SUCCEEDED");
  }

  private String create(String name) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/workflows")
                    .with(user(OWNER))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"description\":\"Test\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.role").value("OWNER"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).at("/data/id").asText();
  }

  private void publish(String workflowId, long revision) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflows/{id}/publish", workflowId)
                .with(user(OWNER))
                .with(csrf())
                .header("If-Match", revision))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.graphSha256").isString());
  }

  private String execute(String workflowId, String key) throws Exception {
    return executeInput(workflowId, key, "{}");
  }

  private String executeInput(String workflowId, String key, String input) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/workflows/{id}/executions", workflowId)
                    .with(user(OWNER))
                    .with(csrf())
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"input\":" + input + "}"))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).at("/data/id").asText();
  }

  private void updateGraph(String workflowId, long revision, String graph) throws Exception {
    String update =
        mapper.writeValueAsString(
            mapper
                .createObjectNode()
                .put("name", "Updated " + workflowId.substring(0, 8))
                .put("description", "Controlled")
                .set("graph", mapper.readTree(graph)));
    mockMvc
        .perform(
            patch("/api/v1/workflows/{id}", workflowId)
                .with(user(OWNER))
                .with(csrf())
                .header("If-Match", revision)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update))
        .andExpect(status().isOk());
  }

  private String approvalId(String executionId) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/workflow-executions/{id}/events", executionId).with(user(OWNER)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).path("data").findValue("approvalId").asText();
  }

  private void decide(String approvalId, String userId, String key, String expectedStatus)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow-approvals/{id}/decisions", approvalId)
                .with(user(userId))
                .with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value(expectedStatus));
  }

  private static String approvalGraph() {
    return """
        {"schemaVersion":1,"nodes":[
        {"id":"start","type":"START","schemaVersion":1,"position":{"x":0,"y":0},"config":{}},
        {"id":"approve","type":"APPROVAL","schemaVersion":1,"position":{"x":200,"y":0},"config":{}},
        {"id":"end","type":"END","schemaVersion":1,"position":{"x":400,"y":0},"config":{}}],
        "edges":[
        {"id":"to_approval","source":"start","sourcePort":"out","target":"approve","targetPort":"in"},
        {"id":"to_end","source":"approve","sourcePort":"out","target":"end","targetPort":"in"}]}
        """;
  }

  private static String conditionGraph() {
    return """
        {"schemaVersion":1,"nodes":[
        {"id":"start","type":"START","schemaVersion":1,"position":{"x":0,"y":0},"config":{}},
        {"id":"choice","type":"CONDITION","schemaVersion":1,"position":{"x":200,"y":0},
        "config":{"field":"status","operator":"EQUALS","value":"ready"}},
        {"id":"end_true","type":"END","schemaVersion":1,"position":{"x":400,"y":0},"config":{}},
        {"id":"end_false","type":"END","schemaVersion":1,"position":{"x":400,"y":200},"config":{}}],
        "edges":[
        {"id":"to_choice","source":"start","sourcePort":"out","target":"choice","targetPort":"in"},
        {"id":"true_path","source":"choice","sourcePort":"true","target":"end_true","targetPort":"in"},
        {"id":"false_path","source":"choice","sourcePort":"false","target":"end_false","targetPort":"in"}]}
        """;
  }

  private static String allApprovalGraph() {
    return """
        {"schemaVersion":1,"nodes":[
        {"id":"start","type":"START","schemaVersion":1,"position":{"x":0,"y":0},"config":{}},
        {"id":"approve","type":"APPROVAL","schemaVersion":1,"position":{"x":200,"y":0},
        "config":{"mode":"ALL","assigneeIds":["11111111-1111-1111-1111-111111111111",
        "22222222-2222-4222-8222-222222222222"]}},
        {"id":"end","type":"END","schemaVersion":1,"position":{"x":400,"y":0},"config":{}}],
        "edges":[
        {"id":"to_approval","source":"start","sourcePort":"out","target":"approve","targetPort":"in"},
        {"id":"to_end","source":"approve","sourcePort":"out","target":"end","targetPort":"in"}]}
        """;
  }
}
