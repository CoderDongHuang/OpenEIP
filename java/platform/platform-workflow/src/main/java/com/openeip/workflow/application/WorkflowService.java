package com.openeip.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.domain.WorkflowRole;
import com.openeip.workflow.domain.entity.WorkflowDefinition;
import com.openeip.workflow.domain.entity.WorkflowMember;
import com.openeip.workflow.domain.entity.WorkflowVersion;
import com.openeip.workflow.domain.repository.WorkflowDefinitionRepository;
import com.openeip.workflow.domain.repository.WorkflowMemberRepository;
import com.openeip.workflow.domain.repository.WorkflowVersionRepository;
import com.openeip.workflow.shared.WorkflowException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {
  public static final String TENANT = "default";
  private static final String DEFAULT_GRAPH =
      """
      {"schemaVersion":1,"nodes":[
        {"id":"start","type":"START","schemaVersion":1,"position":{"x":80,"y":160},"config":{}},
        {"id":"end","type":"END","schemaVersion":1,"position":{"x":420,"y":160},"config":{}}
      ],"edges":[{"id":"start_end","source":"start","sourcePort":"out","target":"end","targetPort":"in"}]}
      """;

  private final WorkflowDefinitionRepository definitions;
  private final WorkflowMemberRepository members;
  private final WorkflowVersionRepository versions;
  private final WorkflowGraphValidator validator;
  private final ObjectMapper mapper;
  private final Clock clock;

  @Autowired
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are shared services.")
  public WorkflowService(
      WorkflowDefinitionRepository definitions,
      WorkflowMemberRepository members,
      WorkflowVersionRepository versions,
      WorkflowGraphValidator validator,
      ObjectMapper mapper) {
    this(definitions, members, versions, validator, mapper, Clock.systemUTC());
  }

  WorkflowService(
      WorkflowDefinitionRepository definitions,
      WorkflowMemberRepository members,
      WorkflowVersionRepository versions,
      WorkflowGraphValidator validator,
      ObjectMapper mapper,
      Clock clock) {
    this.definitions = definitions;
    this.members = members;
    this.versions = versions;
    this.validator = validator;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public WorkflowAccess create(String userId, String name, String description) {
    String validName = name(name);
    if (definitions.existsByTenantIdAndOwnerIdAndNameAndDeletedAtIsNull(
        TENANT, userId, validName)) {
      throw WorkflowException.conflict("Workflow name already exists");
    }
    Instant now = clock.instant();
    WorkflowDefinition workflow =
        definitions.save(
            new WorkflowDefinition(
                UUID.randomUUID().toString(),
                TENANT,
                userId,
                validName,
                description(description),
                validator.canonical(read(DEFAULT_GRAPH)),
                now));
    members.save(
        new WorkflowMember(
            UUID.randomUUID().toString(),
            TENANT,
            workflow.getId(),
            userId,
            WorkflowRole.OWNER,
            now));
    return new WorkflowAccess(workflow, WorkflowRole.OWNER);
  }

  @Transactional(readOnly = true)
  public Page<WorkflowAccess> list(String userId, int page, int size) {
    if (page < 1 || size < 1 || size > 100) {
      throw WorkflowException.invalid("Invalid page");
    }
    return definitions
        .findAccessible(
            TENANT,
            userId,
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "updatedAt")))
        .map(value -> new WorkflowAccess(value, role(value.getId(), userId)));
  }

  @Transactional(readOnly = true)
  public WorkflowAccess get(String userId, String workflowId) {
    WorkflowDefinition workflow = definition(workflowId);
    return new WorkflowAccess(workflow, role(workflowId, userId));
  }

  @Transactional
  public WorkflowAccess update(
      String userId,
      String workflowId,
      long expectedRevision,
      String name,
      String description,
      JsonNode graph) {
    WorkflowAccess access = get(userId, workflowId);
    requireEditor(access.role());
    requireRevision(access.workflow(), expectedRevision);
    String validName = name(name);
    if (definitions.existsByTenantIdAndOwnerIdAndNameAndIdNotAndDeletedAtIsNull(
        TENANT, access.workflow().getOwnerId(), validName, workflowId)) {
      throw WorkflowException.conflict("Workflow name already exists");
    }
    access
        .workflow()
        .updateDraft(
            validName, description(description), validator.canonical(graph), clock.instant());
    return access;
  }

  @Transactional(readOnly = true)
  public WorkflowGraphValidator.ValidationResult validate(String userId, String workflowId) {
    WorkflowAccess access = get(userId, workflowId);
    requireEditor(access.role());
    return validator.validate(read(access.workflow().getGraphJson()));
  }

  @Transactional
  public WorkflowVersion publish(String userId, String workflowId, long expectedRevision) {
    WorkflowAccess access = get(userId, workflowId);
    requireEditor(access.role());
    requireRevision(access.workflow(), expectedRevision);
    String canonical = validator.canonical(read(access.workflow().getGraphJson()));
    int number =
        access.workflow().getPublishedVersion() == null
            ? 1
            : access.workflow().getPublishedVersion() + 1;
    Instant now = clock.instant();
    WorkflowVersion version =
        versions.save(
            new WorkflowVersion(
                UUID.randomUUID().toString(),
                TENANT,
                workflowId,
                number,
                WorkflowGraphValidator.sha256(canonical),
                canonical,
                userId,
                now));
    access.workflow().publish(number, now);
    return version;
  }

  @Transactional(readOnly = true)
  public List<WorkflowVersion> versions(String userId, String workflowId) {
    get(userId, workflowId);
    return versions.findAllByTenantIdAndWorkflowIdOrderByVersionDesc(TENANT, workflowId);
  }

  @Transactional
  public WorkflowAccess restore(String userId, String workflowId, int version) {
    WorkflowAccess access = get(userId, workflowId);
    requireEditor(access.role());
    WorkflowVersion stored =
        versions
            .findByTenantIdAndWorkflowIdAndVersion(TENANT, workflowId, version)
            .orElseThrow(WorkflowException::notFound);
    access.workflow().restore(stored.getGraphJson(), clock.instant());
    return access;
  }

  @Transactional
  public void delete(String userId, String workflowId) {
    WorkflowAccess access = get(userId, workflowId);
    if (access.role() != WorkflowRole.OWNER) {
      throw WorkflowException.forbidden();
    }
    access.workflow().delete(clock.instant());
  }

  WorkflowDefinition definition(String workflowId) {
    validUuid(workflowId);
    return definitions
        .findByIdAndTenantIdAndDeletedAtIsNull(workflowId, TENANT)
        .orElseThrow(WorkflowException::notFound);
  }

  WorkflowRole role(String workflowId, String userId) {
    return members
        .findByTenantIdAndWorkflowIdAndUserId(TENANT, workflowId, userId)
        .map(WorkflowMember::getRole)
        .orElseThrow(WorkflowException::notFound);
  }

  static void requireEditor(WorkflowRole role) {
    if (!role.canEdit()) {
      throw WorkflowException.forbidden();
    }
  }

  static void validUuid(String value) {
    if (value == null) {
      throw WorkflowException.invalid("Invalid resource identifier");
    }
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw WorkflowException.invalid("Invalid resource identifier");
    }
  }

  private static void requireRevision(WorkflowDefinition workflow, long expected) {
    if (workflow.getDraftRevision() != expected) {
      throw WorkflowException.conflict("Workflow draft revision is stale");
    }
  }

  private static String name(String value) {
    if (value == null || value.trim().isEmpty() || value.trim().length() > 120) {
      throw WorkflowException.invalid("Workflow name must contain 1 to 120 characters");
    }
    return value.trim();
  }

  private static String description(String value) {
    String result = value == null ? "" : value.trim();
    if (result.length() > 2000) {
      throw WorkflowException.invalid("Description exceeds 2000 characters");
    }
    return result;
  }

  private JsonNode read(String value) {
    try {
      return mapper.readTree(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored workflow graph is invalid", exception);
    }
  }

  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "Transaction-scoped entity.")
  public record WorkflowAccess(WorkflowDefinition workflow, WorkflowRole role) {}
}
