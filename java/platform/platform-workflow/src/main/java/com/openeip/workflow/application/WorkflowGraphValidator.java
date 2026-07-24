package com.openeip.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openeip.workflow.shared.WorkflowException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WorkflowGraphValidator {
  private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");
  private static final Pattern PORT = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,31}$");
  private static final Set<String> GRAPH_FIELDS = Set.of("schemaVersion", "nodes", "edges");
  private static final Set<String> NODE_FIELDS =
      Set.of("id", "type", "schemaVersion", "position", "config");
  private static final Set<String> POSITION_FIELDS = Set.of("x", "y");
  private static final Set<String> EDGE_FIELDS =
      Set.of("id", "source", "sourcePort", "target", "targetPort");
  private static final Set<String> TYPES =
      Set.of(
          "START",
          "END",
          "LLM",
          "AGENT",
          "TOOL",
          "CONDITION",
          "LOOP",
          "APPROVAL",
          "DELAY",
          "WEBHOOK");
  private final ObjectMapper mapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "ObjectMapper is application scoped.")
  public WorkflowGraphValidator(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public ValidationResult validate(JsonNode graph) {
    List<ValidationError> errors = new ArrayList<>();
    if (graph == null
        || !graph.isObject()
        || !hasOnlyFields(graph, GRAPH_FIELDS)
        || graph.path("schemaVersion").asInt(-1) != 1) {
      return new ValidationResult(
          false, List.of(new ValidationError("WF-V-001", null, "Invalid graph schema")));
    }
    JsonNode nodes = graph.path("nodes");
    JsonNode edges = graph.path("edges");
    if (!nodes.isArray()
        || nodes.size() < 2
        || nodes.size() > 100
        || !edges.isArray()
        || edges.isEmpty()
        || edges.size() > 200) {
      return new ValidationResult(
          false, List.of(new ValidationError("WF-V-002", null, "Graph limits are invalid")));
    }
    Map<String, String> types = new HashMap<>();
    Map<String, Integer> incoming = new HashMap<>();
    Map<String, List<String>> outgoing = new HashMap<>();
    int starts = 0;
    int ends = 0;
    for (JsonNode node : nodes) {
      String id = node.path("id").asText();
      String type = node.path("type").asText();
      if (!node.isObject()
          || !hasOnlyFields(node, NODE_FIELDS)
          || !IDENTIFIER.matcher(id).matches()
          || !TYPES.contains(type)
          || node.path("schemaVersion").asInt(-1) != 1) {
        errors.add(new ValidationError("WF-V-003", validId(id), "Invalid node contract"));
        continue;
      }
      if (types.putIfAbsent(id, type) != null) {
        errors.add(new ValidationError("WF-V-004", id, "Duplicate node identifier"));
      }
      JsonNode position = node.path("position");
      if (!validPosition(position)
          || !node.path("config").isObject()
          || node.path("config").size() > 50
          || !validConfig(type, node.path("config"))) {
        errors.add(new ValidationError("WF-V-003", id, "Invalid node contract"));
      }
      starts += type.equals("START") ? 1 : 0;
      ends += type.equals("END") ? 1 : 0;
      incoming.put(id, 0);
      outgoing.put(id, new ArrayList<>());
    }
    if (starts != 1 || ends < 1) {
      errors.add(
          new ValidationError("WF-V-005", null, "Graph requires one Start and at least one End"));
    }
    Set<String> edgeIds = new HashSet<>();
    for (JsonNode edge : edges) {
      String id = edge.path("id").asText();
      String source = edge.path("source").asText();
      String target = edge.path("target").asText();
      if (!edge.isObject()
          || !hasOnlyFields(edge, EDGE_FIELDS)
          || !IDENTIFIER.matcher(id).matches()
          || !edgeIds.add(id)
          || !types.containsKey(source)
          || !types.containsKey(target)
          || !PORT.matcher(edge.path("sourcePort").asText()).matches()
          || !PORT.matcher(edge.path("targetPort").asText()).matches()
          || source.equals(target)) {
        errors.add(new ValidationError("WF-V-006", null, "Invalid edge contract"));
        continue;
      }
      outgoing.get(source).add(target);
      incoming.computeIfPresent(target, (key, value) -> value + 1);
      if (outgoing.get(source).size() > 10) {
        errors.add(new ValidationError("WF-V-007", source, "Node fan-out exceeds 10"));
      }
    }
    if (errors.isEmpty()) {
      validateReachabilityAndCycles(types, incoming, outgoing, errors);
    }
    return new ValidationResult(errors.isEmpty(), List.copyOf(errors));
  }

  private static void validateReachabilityAndCycles(
      Map<String, String> types,
      Map<String, Integer> incoming,
      Map<String, List<String>> outgoing,
      List<ValidationError> errors) {
    String start =
        types.entrySet().stream()
            .filter(e -> e.getValue().equals("START"))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    if (start == null) {
      return;
    }
    Set<String> reached = new HashSet<>();
    ArrayDeque<String> visit = new ArrayDeque<>();
    visit.add(start);
    while (!visit.isEmpty()) {
      String node = visit.removeFirst();
      if (reached.add(node)) {
        visit.addAll(outgoing.getOrDefault(node, List.of()));
      }
    }
    if (reached.size() != types.size()) {
      errors.add(new ValidationError("WF-V-008", null, "Graph contains unreachable nodes"));
      return;
    }
    Map<String, Integer> degrees = new HashMap<>(incoming);
    ArrayDeque<String> ready = new ArrayDeque<>();
    degrees.forEach(
        (node, degree) -> {
          if (degree == 0) {
            ready.add(node);
          }
        });
    int visited = 0;
    while (!ready.isEmpty()) {
      String source = ready.removeFirst();
      visited++;
      for (String target : outgoing.getOrDefault(source, List.of())) {
        if (degrees.compute(target, (key, value) -> value - 1) == 0) {
          ready.add(target);
        }
      }
    }
    if (visited != types.size()) {
      errors.add(new ValidationError("WF-V-009", null, "Arbitrary graph cycles are not allowed"));
    }
  }

  public String canonical(JsonNode graph) {
    ValidationResult result = validate(graph);
    if (!result.valid()) {
      throw WorkflowException.invalid(result.errors().getFirst().message());
    }
    try {
      return mapper.writeValueAsString(sort(graph));
    } catch (JsonProcessingException exception) {
      throw WorkflowException.invalid("Invalid workflow graph");
    }
  }

  private JsonNode sort(JsonNode value) {
    if (value.isObject()) {
      ObjectNode result = mapper.createObjectNode();
      List<String> names = new ArrayList<>();
      value.fieldNames().forEachRemaining(names::add);
      names.stream()
          .sorted(Comparator.naturalOrder())
          .forEach(name -> result.set(name, sort(value.get(name))));
      return result;
    }
    if (value.isArray()) {
      ArrayNode result = mapper.createArrayNode();
      Iterator<JsonNode> values = value.elements();
      values.forEachRemaining(item -> result.add(sort(item)));
      return result;
    }
    return value;
  }

  public static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String validId(String id) {
    return IDENTIFIER.matcher(id).matches() ? id : null;
  }

  private static boolean hasOnlyFields(JsonNode value, Set<String> allowed) {
    if (!value.isObject()) {
      return false;
    }
    Iterator<String> names = value.fieldNames();
    while (names.hasNext()) {
      if (!allowed.contains(names.next())) {
        return false;
      }
    }
    return true;
  }

  private static boolean validPosition(JsonNode position) {
    if (!position.isObject() || !hasOnlyFields(position, POSITION_FIELDS)) {
      return false;
    }
    JsonNode x = position.path("x");
    JsonNode y = position.path("y");
    return x.isNumber()
        && y.isNumber()
        && Double.isFinite(x.asDouble())
        && Double.isFinite(y.asDouble())
        && Math.abs(x.asDouble()) <= 100_000
        && Math.abs(y.asDouble()) <= 100_000;
  }

  private static boolean validConfig(String type, JsonNode config) {
    if (type.equals("START") || type.equals("END")) {
      return config.isEmpty();
    }
    if (type.equals("DELAY")) {
      return hasOnlyFields(config, Set.of("seconds"))
          && config.path("seconds").canConvertToLong()
          && config.path("seconds").asLong() >= 1
          && config.path("seconds").asLong() <= 86_400;
    }
    if (type.equals("APPROVAL")) {
      if (!hasOnlyFields(config, Set.of("assigneeIds", "mode"))) {
        return false;
      }
      JsonNode assignees = config.path("assigneeIds");
      if (!assignees.isMissingNode()
          && (!assignees.isArray() || assignees.size() > 50 || !allText(assignees))) {
        return false;
      }
      String mode = config.path("mode").asText("ANY");
      return mode.equals("ANY")
          || (mode.equals("ALL") && !assignees.isMissingNode() && !assignees.isEmpty());
    }
    if (type.equals("CONDITION")) {
      return hasOnlyFields(config, Set.of("field", "operator", "value"))
          && IDENTIFIER.matcher(config.path("field").asText()).matches()
          && Set.of("EQUALS", "NOT_EQUALS", "EXISTS").contains(config.path("operator").asText());
    }
    if (type.equals("LOOP")) {
      return hasOnlyFields(config, Set.of("maxIterations"))
          && config.path("maxIterations").canConvertToInt()
          && config.path("maxIterations").asInt() >= 1
          && config.path("maxIterations").asInt() <= 100;
    }
    return true;
  }

  private static boolean allText(JsonNode values) {
    for (JsonNode value : values) {
      if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 36) {
        return false;
      }
    }
    return true;
  }

  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "The compact constructor stores an immutable defensive copy.")
  public record ValidationResult(boolean valid, List<ValidationError> errors) {
    public ValidationResult {
      errors = List.copyOf(errors);
    }
  }

  public record ValidationError(String code, String nodeId, String message) {}
}
