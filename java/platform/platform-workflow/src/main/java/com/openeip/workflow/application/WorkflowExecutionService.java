package com.openeip.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.domain.ExecutionStatus;
import com.openeip.workflow.domain.WorkflowRole;
import com.openeip.workflow.domain.entity.WorkflowApproval;
import com.openeip.workflow.domain.entity.WorkflowApprovalDecision;
import com.openeip.workflow.domain.entity.WorkflowEvent;
import com.openeip.workflow.domain.entity.WorkflowExecution;
import com.openeip.workflow.domain.entity.WorkflowNodeExecution;
import com.openeip.workflow.domain.entity.WorkflowVersion;
import com.openeip.workflow.domain.repository.WorkflowApprovalDecisionRepository;
import com.openeip.workflow.domain.repository.WorkflowApprovalRepository;
import com.openeip.workflow.domain.repository.WorkflowEventRepository;
import com.openeip.workflow.domain.repository.WorkflowExecutionRepository;
import com.openeip.workflow.domain.repository.WorkflowNodeExecutionRepository;
import com.openeip.workflow.domain.repository.WorkflowVersionRepository;
import com.openeip.workflow.shared.WorkflowException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class WorkflowExecutionService {
  private static final Pattern IDEMPOTENCY = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
  private final WorkflowService workflows;
  private final WorkflowVersionRepository versions;
  private final WorkflowExecutionRepository executions;
  private final WorkflowNodeExecutionRepository nodes;
  private final WorkflowApprovalRepository approvals;
  private final WorkflowApprovalDecisionRepository approvalDecisions;
  private final WorkflowEventRepository events;
  private final WorkflowEventService eventService;
  private final WorkflowEventStream stream;
  private final ObjectMapper mapper;
  private final Clock clock = Clock.systemUTC();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are shared services.")
  public WorkflowExecutionService(
      WorkflowService workflows,
      WorkflowVersionRepository versions,
      WorkflowExecutionRepository executions,
      WorkflowNodeExecutionRepository nodes,
      WorkflowApprovalRepository approvals,
      WorkflowApprovalDecisionRepository approvalDecisions,
      WorkflowEventRepository events,
      WorkflowEventService eventService,
      WorkflowEventStream stream,
      ObjectMapper mapper) {
    this.workflows = workflows;
    this.versions = versions;
    this.executions = executions;
    this.nodes = nodes;
    this.approvals = approvals;
    this.approvalDecisions = approvalDecisions;
    this.events = events;
    this.eventService = eventService;
    this.stream = stream;
    this.mapper = mapper;
  }

  @Transactional
  public WorkflowExecution trigger(
      String userId, String workflowId, String idempotencyKey, JsonNode input) {
    WorkflowService.WorkflowAccess access = workflows.get(userId, workflowId);
    if (!access.role().canRun()) {
      throw WorkflowException.forbidden();
    }
    return triggerInternal(workflowId, idempotencyKey, input, "MANUAL");
  }

  @Transactional
  public WorkflowExecution triggerSystem(
      String workflowId, String idempotencyKey, JsonNode input, String triggerType) {
    workflows.definition(workflowId);
    return triggerInternal(workflowId, idempotencyKey, input, triggerType);
  }

  private WorkflowExecution triggerInternal(
      String workflowId, String idempotencyKey, JsonNode input, String triggerType) {
    validIdempotency(idempotencyKey);
    var duplicate =
        executions.findByTenantIdAndWorkflowIdAndIdempotencyKey(
            WorkflowService.TENANT, workflowId, idempotencyKey);
    if (duplicate.isPresent()) {
      return duplicate.get();
    }
    var definition = workflows.definition(workflowId);
    if (definition.getPublishedVersion() == null) {
      throw WorkflowException.conflict("Workflow must be published before execution");
    }
    JsonNode validInput = input == null ? mapper.createObjectNode() : input;
    if (!validInput.isObject() || validInput.size() > 100 || encodedSize(validInput) > 64 * 1024) {
      throw WorkflowException.invalid("Execution input must be a bounded object");
    }
    Instant now = clock.instant();
    WorkflowExecution execution =
        executions.save(
            new WorkflowExecution(
                UUID.randomUUID().toString(),
                WorkflowService.TENANT,
                workflowId,
                definition.getPublishedVersion(),
                triggerType,
                idempotencyKey,
                json(validInput),
                now));
    eventService.append(
        execution, "workflow.execution.queued", null, Map.of("triggerType", triggerType));
    advance(execution);
    return execution;
  }

  @Transactional(readOnly = true)
  public Page<WorkflowExecution> list(String userId, String workflowId, int page, int size) {
    workflows.get(userId, workflowId);
    if (page < 1 || size < 1 || size > 100) {
      throw WorkflowException.invalid("Invalid page");
    }
    return executions.findAllByTenantIdAndWorkflowId(
        WorkflowService.TENANT,
        workflowId,
        PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt")));
  }

  @Transactional(readOnly = true)
  public WorkflowExecution get(String userId, String executionId) {
    WorkflowExecution execution = execution(executionId);
    workflows.get(userId, execution.getWorkflowId());
    return execution;
  }

  @Transactional
  public WorkflowExecution cancel(String userId, String executionId) {
    WorkflowExecution execution = get(userId, executionId);
    if (!workflows.role(execution.getWorkflowId(), userId).canRun()) {
      throw WorkflowException.forbidden();
    }
    if (execution.getStatus().terminal()) {
      throw WorkflowException.conflict("Execution is already terminal");
    }
    execution.transition(ExecutionStatus.CANCELLED, clock.instant());
    eventService.append(execution, "workflow.execution.cancelled", null, Map.of());
    return execution;
  }

  @Transactional
  public WorkflowExecution retry(
      String userId, String executionId, String nodeId, String idempotencyKey) {
    validIdempotency(idempotencyKey);
    WorkflowExecution execution = get(userId, executionId);
    if (!workflows.role(execution.getWorkflowId(), userId).canRun()) {
      throw WorkflowException.forbidden();
    }
    if (execution.getStatus() != ExecutionStatus.FAILED) {
      throw WorkflowException.conflict("Only failed executions can retry a node");
    }
    WorkflowNodeExecution failed =
        nodes
            .findFirstByExecutionIdAndNodeIdOrderByAttemptDesc(executionId, nodeId)
            .filter(value -> value.getStatus().equals("FAILED"))
            .orElseThrow(WorkflowException::notFound);
    failed.status("RETRYING", clock.instant());
    execution.resume(clock.instant());
    eventService.append(
        execution,
        "workflow.node.retry.requested",
        nodeId,
        Map.of("idempotencyKey", idempotencyKey));
    advance(execution);
    return execution;
  }

  @Transactional
  public WorkflowExecution decide(
      String userId, String approvalId, String decision, String comment, String idempotencyKey) {
    validIdempotency(idempotencyKey);
    WorkflowApproval approval =
        approvals
            .findByIdAndTenantId(approvalId, WorkflowService.TENANT)
            .orElseThrow(WorkflowException::notFound);
    WorkflowExecution execution = get(userId, approval.getExecutionId());
    WorkflowRole role = workflows.role(execution.getWorkflowId(), userId);
    if (!role.canApprove() || !assignee(approval, userId)) {
      throw WorkflowException.forbidden();
    }
    if (approvalDecisions
        .findByTenantIdAndApprovalIdAndIdempotencyKey(
            WorkflowService.TENANT, approvalId, idempotencyKey)
        .isPresent()) {
      return execution;
    }
    if (!approval.getStatus().equals("PENDING")) {
      throw WorkflowException.conflict("Approval is already decided");
    }
    if (approvalDecisions.existsByTenantIdAndApprovalIdAndAssigneeId(
        WorkflowService.TENANT, approvalId, userId)) {
      throw WorkflowException.conflict("Assignee already decided this approval");
    }
    String validComment = comment == null ? "" : comment.trim();
    if (validComment.length() > 1000) {
      throw WorkflowException.invalid("Approval comment is too long");
    }
    Instant now = clock.instant();
    approvalDecisions.save(
        new WorkflowApprovalDecision(
            UUID.randomUUID().toString(),
            WorkflowService.TENANT,
            approvalId,
            userId,
            decision,
            validComment,
            idempotencyKey,
            now));
    WorkflowNodeExecution node =
        nodes
            .findFirstByExecutionIdAndNodeIdOrderByAttemptDesc(
                execution.getId(), approval.getNodeId())
            .orElseThrow(WorkflowException::notFound);
    eventService.append(
        execution,
        "workflow.approval.decided",
        approval.getNodeId(),
        Map.of("approvalId", approvalId, "decision", decision));
    if (decision.equals("REJECT")) {
      approval.decide(userId, decision, validComment, now);
      node.fail("WF-E-005", now);
      execution.fail("WF-E-005", now);
      eventService.append(
          execution,
          "workflow.execution.failed",
          approval.getNodeId(),
          Map.of("failureCode", "WF-E-005"));
    } else if (approvalSatisfied(approval)) {
      approval.decide(userId, decision, validComment, now);
      node.succeed("{}", now);
      eventService.append(
          execution,
          "workflow.node.completed",
          approval.getNodeId(),
          Map.of("attempt", node.getAttempt()));
      execution.resume(now);
      advance(execution);
    }
    return execution;
  }

  @Transactional(readOnly = true)
  public List<WorkflowEvent> events(String userId, String executionId, long after) {
    get(userId, executionId);
    if (after < 0) {
      throw WorkflowException.invalid("Invalid event sequence");
    }
    return events.findAllByExecutionIdAndSequenceGreaterThanOrderBySequenceAsc(executionId, after);
  }

  @Transactional(readOnly = true)
  public SseEmitter stream(String userId, String executionId, long after) {
    return stream.open(executionId, events(userId, executionId, after));
  }

  @Transactional
  public void advanceDue() {
    Instant now = clock.instant();
    for (WorkflowExecution execution :
        executions.findTop50ByStatusInOrderByUpdatedAtAsc(
            List.of(ExecutionStatus.QUEUED, ExecutionStatus.RETRY_WAIT))) {
      advance(execution);
    }
    for (WorkflowExecution execution :
        executions.findTop50ByStatusInAndResumeAtBeforeOrderByUpdatedAtAsc(
            List.of(ExecutionStatus.WAITING_DELAY), now)) {
      advance(execution);
    }
  }

  void advance(WorkflowExecution execution) {
    Instant now = clock.instant();
    if (execution.getStatus().terminal()
        || execution.getStatus() == ExecutionStatus.WAITING_APPROVAL) {
      return;
    }
    if (execution.getStatus() == ExecutionStatus.WAITING_DELAY) {
      if (execution.getResumeAt() != null && execution.getResumeAt().isAfter(now)) {
        return;
      }
      nodes.findAllByExecutionIdOrderByCreatedAtAsc(execution.getId()).stream()
          .filter(node -> node.getStatus().equals("WAITING"))
          .forEach(
              node -> {
                node.succeed("{}", now);
                eventService.append(
                    execution, "workflow.node.completed", node.getNodeId(), Map.of());
              });
      execution.resume(now);
    } else if (execution.getStatus() == ExecutionStatus.QUEUED) {
      execution.transition(ExecutionStatus.RUNNING, now);
      eventService.append(execution, "workflow.execution.started", null, Map.of());
    }
    WorkflowVersion version =
        versions
            .findByTenantIdAndWorkflowIdAndVersion(
                WorkflowService.TENANT, execution.getWorkflowId(), execution.getWorkflowVersion())
            .orElseThrow(WorkflowException::notFound);
    JsonNode graph = read(version.getGraphJson());
    for (int transitions = 0; transitions < 200; transitions++) {
      List<WorkflowNodeExecution> attempts =
          nodes.findAllByExecutionIdOrderByCreatedAtAsc(execution.getId());
      Map<String, WorkflowNodeExecution> latest = latest(attempts);
      if (endsSucceeded(graph, latest)) {
        execution.transition(ExecutionStatus.SUCCEEDED, clock.instant());
        eventService.append(execution, "workflow.execution.completed", null, Map.of());
        return;
      }
      JsonNode ready = readyNode(graph, latest);
      if (ready == null) {
        execution.fail("WF-E-004", clock.instant());
        eventService.append(
            execution, "workflow.execution.failed", null, Map.of("failureCode", "WF-E-004"));
        return;
      }
      if (runNode(execution, ready, latest.get(ready.path("id").asText()))) {
        return;
      }
    }
    execution.fail("WF-E-006", clock.instant());
    eventService.append(
        execution, "workflow.execution.failed", null, Map.of("failureCode", "WF-E-006"));
  }

  private boolean runNode(
      WorkflowExecution execution, JsonNode definition, WorkflowNodeExecution previous) {
    Instant now = clock.instant();
    String nodeId = definition.path("id").asText();
    int attempt = previous == null ? 1 : previous.getAttempt() + 1;
    WorkflowNodeExecution node =
        nodes.save(
            new WorkflowNodeExecution(
                UUID.randomUUID().toString(),
                WorkflowService.TENANT,
                execution.getId(),
                nodeId,
                attempt,
                execution.getId() + ":" + nodeId + ":0",
                now));
    node.status("RUNNING", now);
    eventService.append(execution, "workflow.node.started", nodeId, Map.of("attempt", attempt));
    String type = definition.path("type").asText();
    if (type.equals("APPROVAL")) {
      JsonNode assignees = definition.path("config").path("assigneeIds");
      String assigneesJson = assignees.isArray() ? json(assignees) : "[]";
      WorkflowApproval approval =
          approvals
              .findByExecutionIdAndNodeId(execution.getId(), nodeId)
              .map(
                  existing -> {
                    existing.reopen();
                    approvalDecisions.deleteAllByTenantIdAndApprovalId(
                        WorkflowService.TENANT, existing.getId());
                    return existing;
                  })
              .orElseGet(
                  () ->
                      approvals.save(
                          new WorkflowApproval(
                              UUID.randomUUID().toString(),
                              WorkflowService.TENANT,
                              execution.getId(),
                              nodeId,
                              assigneesJson,
                              definition.path("config").path("mode").asText("ANY"),
                              now)));
      node.status("WAITING", now);
      execution.transition(ExecutionStatus.WAITING_APPROVAL, now);
      eventService.append(
          execution, "workflow.approval.requested", nodeId, Map.of("approvalId", approval.getId()));
      return true;
    }
    if (type.equals("DELAY")) {
      long seconds =
          Math.max(1, Math.min(86_400, definition.path("config").path("seconds").asLong(1)));
      node.status("WAITING", now);
      execution.waitUntil(
          ExecutionStatus.WAITING_DELAY, now.plus(seconds, ChronoUnit.SECONDS), now);
      eventService.append(
          execution, "workflow.execution.waiting.delay", nodeId, Map.of("seconds", seconds));
      return true;
    }
    Map<String, Object> output = Map.of();
    if (type.equals("CONDITION")) {
      boolean matched = conditionMatches(read(execution.getInputJson()), definition.path("config"));
      output = Map.of("selectedPort", matched ? "true" : "false");
    } else if (type.equals("LOOP")) {
      output = Map.of("iterations", definition.path("config").path("maxIterations").asInt());
    }
    node.succeed(json(output), now);
    eventService.append(execution, "workflow.node.completed", nodeId, Map.of("attempt", attempt));
    return false;
  }

  private static Map<String, WorkflowNodeExecution> latest(List<WorkflowNodeExecution> attempts) {
    Map<String, WorkflowNodeExecution> result = new HashMap<>();
    attempts.forEach(node -> result.put(node.getNodeId(), node));
    return result;
  }

  private JsonNode readyNode(JsonNode graph, Map<String, WorkflowNodeExecution> latest) {
    Set<String> active = activeNodes(graph, latest);
    for (JsonNode node : graph.path("nodes")) {
      String id = node.path("id").asText();
      if (!active.contains(id)) {
        continue;
      }
      WorkflowNodeExecution current = latest.get(id);
      if (current != null && !current.getStatus().equals("RETRYING")) {
        continue;
      }
      Set<String> incoming = activeIncoming(graph, latest, active, id);
      boolean ready =
          node.path("type").asText().equals("START")
              || (!incoming.isEmpty()
                  && incoming.stream()
                      .allMatch(
                          source ->
                              latest.containsKey(source)
                                  && latest.get(source).getStatus().equals("SUCCEEDED")));
      if (ready) {
        return node;
      }
    }
    return null;
  }

  private boolean endsSucceeded(JsonNode graph, Map<String, WorkflowNodeExecution> latest) {
    Set<String> active = activeNodes(graph, latest);
    List<String> ends = new ArrayList<>();
    for (JsonNode node : graph.path("nodes")) {
      if (node.path("type").asText().equals("END") && active.contains(node.path("id").asText())) {
        ends.add(node.path("id").asText());
      }
    }
    return !ends.isEmpty()
        && ends.stream()
            .allMatch(
                id -> latest.containsKey(id) && latest.get(id).getStatus().equals("SUCCEEDED"));
  }

  private Set<String> activeNodes(JsonNode graph, Map<String, WorkflowNodeExecution> latest) {
    Set<String> result = new HashSet<>();
    for (JsonNode node : graph.path("nodes")) {
      if (node.path("type").asText().equals("START")) {
        result.add(node.path("id").asText());
      }
    }
    boolean changed;
    do {
      changed = false;
      for (JsonNode edge : graph.path("edges")) {
        String source = edge.path("source").asText();
        if (result.contains(source) && edgeSelected(graph, latest, edge)) {
          changed |= result.add(edge.path("target").asText());
        }
      }
    } while (changed);
    return result;
  }

  private Set<String> activeIncoming(
      JsonNode graph,
      Map<String, WorkflowNodeExecution> latest,
      Set<String> active,
      String target) {
    Set<String> result = new HashSet<>();
    for (JsonNode edge : graph.path("edges")) {
      String source = edge.path("source").asText();
      if (edge.path("target").asText().equals(target)
          && active.contains(source)
          && edgeSelected(graph, latest, edge)) {
        result.add(source);
      }
    }
    return result;
  }

  private boolean edgeSelected(
      JsonNode graph, Map<String, WorkflowNodeExecution> latest, JsonNode edge) {
    String source = edge.path("source").asText();
    JsonNode definition = findNode(graph, source);
    if (definition == null || !definition.path("type").asText().equals("CONDITION")) {
      return true;
    }
    WorkflowNodeExecution attempt = latest.get(source);
    return attempt != null
        && attempt.getStatus().equals("SUCCEEDED")
        && edge.path("sourcePort")
            .asText()
            .equals(read(attempt.getOutputJson()).path("selectedPort").asText());
  }

  private static JsonNode findNode(JsonNode graph, String id) {
    for (JsonNode node : graph.path("nodes")) {
      if (node.path("id").asText().equals(id)) {
        return node;
      }
    }
    return null;
  }

  private static boolean conditionMatches(JsonNode input, JsonNode config) {
    JsonNode actual = input.path(config.path("field").asText());
    String operator = config.path("operator").asText();
    if (operator.equals("EXISTS")) {
      return !actual.isMissingNode() && !actual.isNull();
    }
    boolean equals = actual.equals(config.path("value"));
    return operator.equals("EQUALS") ? equals : !equals;
  }

  private boolean assignee(WorkflowApproval approval, String userId) {
    JsonNode values = read(approval.getAssigneesJson());
    if (values.isEmpty()) {
      return true;
    }
    for (JsonNode value : values) {
      if (value.asText().equals(userId)) {
        return true;
      }
    }
    return false;
  }

  private boolean approvalSatisfied(WorkflowApproval approval) {
    if (approval.getDecisionMode().equals("ANY")) {
      return true;
    }
    JsonNode assignees = read(approval.getAssigneesJson());
    long approved =
        approvalDecisions.countByTenantIdAndApprovalIdAndDecision(
            WorkflowService.TENANT, approval.getId(), "APPROVE");
    return !assignees.isEmpty() && approved >= assignees.size();
  }

  private WorkflowExecution execution(String executionId) {
    WorkflowService.validUuid(executionId);
    return executions
        .findByIdAndTenantId(executionId, WorkflowService.TENANT)
        .orElseThrow(WorkflowException::notFound);
  }

  private static void validIdempotency(String value) {
    if (value == null || !IDEMPOTENCY.matcher(value).matches()) {
      throw WorkflowException.invalid("Invalid idempotency key");
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw WorkflowException.invalid("Invalid JSON payload");
    }
  }

  private int encodedSize(JsonNode value) {
    try {
      return mapper.writeValueAsBytes(value).length;
    } catch (JsonProcessingException exception) {
      throw WorkflowException.invalid("Invalid JSON payload");
    }
  }

  private JsonNode read(String value) {
    try {
      return mapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored workflow JSON is invalid", exception);
    }
  }
}
