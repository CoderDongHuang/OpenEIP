package com.openeip.governance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditAppendCommand;
import com.openeip.governance.domain.audit.AuditIntegrity;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.shared.exception.GovernanceAuditException;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded tenant-scoped read model for the Governance management API. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JdbcTemplate and ObjectMapper are application-scoped collaborators.")
public class GovernanceReadService {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final Duration MAX_AUDIT_RANGE = Duration.ofDays(31);
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public GovernanceReadService(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> currentTenant() {
    UUID tenantId = tenantId();
    return jdbc
        .query(
            """
            SELECT id, display_name, slug, state, policy_version, revision, created_at, updated_at
            FROM governance_tenants WHERE id = ? AND tenant_id = ?
            """,
            (rs, row) -> tenant(rs),
            tenantId.toString(),
            tenantId.toString())
        .stream()
        .findFirst()
        .orElseThrow(() -> GovernanceCatalogException.invalid("Tenant was not found"));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> memberships(int limit) {
    UUID tenantId = tenantId();
    return jdbc.query(
        """
        SELECT id, principal_id, organization_id, roles_json, state, policy_version,
               revision, created_at, updated_at
        FROM governance_memberships WHERE tenant_id = ?
        ORDER BY created_at DESC, id DESC LIMIT ?
        """,
        (rs, row) -> membership(rs),
        tenantId.toString(),
        boundedLimit(limit));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> audits(
      String action, String resourceType, String outcome, Instant from, Instant to, int limit) {
    UUID tenantId = tenantId();
    validateRange(from, to);
    var sql = new StringBuilder("SELECT * FROM governance_audit_records WHERE tenant_id = ?");
    var values = new ArrayList<Object>();
    values.add(tenantId.toString());
    append(sql, values, "action", action);
    append(sql, values, "resource_type", resourceType);
    append(sql, values, "outcome", outcome == null ? null : outcome.toUpperCase());
    if (from != null) {
      sql.append(" AND occurred_at >= ?");
      values.add(java.sql.Timestamp.from(from));
    }
    if (to != null) {
      sql.append(" AND occurred_at <= ?");
      values.add(java.sql.Timestamp.from(to));
    }
    sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
    values.add(boundedLimit(limit));
    return jdbc.query(sql.toString(), (rs, row) -> audit(rs), values.toArray());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> verifyAudit(Instant from, Instant to) {
    UUID tenantId = tenantId();
    validateRange(from, to);
    if (from == null || to == null) {
      throw GovernanceAuditException.invalid("Audit verification requires from and to");
    }
    List<AuditVerificationRow> rows =
        jdbc.query(
            """
            SELECT * FROM governance_audit_records
            WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at <= ?
            ORDER BY occurred_at, id
            LIMIT 1001
            """,
            (rs, row) -> verificationRow(rs),
            tenantId.toString(),
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to));
    if (rows.size() > 1000) {
      throw GovernanceAuditException.invalid("Audit verification range exceeds 1000 records");
    }
    String previous = rows.isEmpty() ? null : precedingHash(tenantId, rows.getFirst());
    boolean valid = true;
    for (AuditVerificationRow row : rows) {
      if (previous != null && !previous.equals(row.previousHash())) {
        valid = false;
        break;
      }
      String calculated =
          AuditIntegrity.recordHash(row.command(), row.previousHash(), canonical(row.summary()));
      if (!calculated.equals(row.recordHash())) {
        valid = false;
        break;
      }
      previous = row.recordHash();
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("valid", valid);
    result.put("recordCount", rows.size());
    result.put("from", from);
    result.put("to", to);
    result.put("lastHash", previous);
    return result;
  }

  private String precedingHash(UUID tenantId, AuditVerificationRow first) {
    return jdbc
        .query(
            """
            SELECT record_hash FROM governance_audit_records
            WHERE tenant_id = ?
              AND (occurred_at < ? OR (occurred_at = ? AND id < ?))
            ORDER BY occurred_at DESC, id DESC LIMIT 1
            """,
            (rs, row) -> rs.getString(1),
            tenantId.toString(),
            java.sql.Timestamp.from(first.command().occurredAt()),
            java.sql.Timestamp.from(first.command().occurredAt()),
            first.id().toString())
        .stream()
        .findFirst()
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> traces(String traceId, int limit) {
    if (traceId == null || !traceId.matches("[a-f0-9]{16,32}")) {
      throw GovernanceCatalogException.invalid("traceId is invalid");
    }
    return jdbc.query(
        """
        SELECT id, trace_id, request_id, execution_id, module, operation, outcome,
               duration_ms, safe_attributes_json, occurred_at
        FROM governance_trace_links
        WHERE tenant_id = ? AND trace_id = ?
        ORDER BY occurred_at, id LIMIT ?
        """,
        (rs, row) -> trace(rs),
        tenantId().toString(),
        traceId,
        boundedLimit(limit));
  }

  private UUID tenantId() {
    var context = TenantContextHolder.required();
    if (context.expiredAt(Instant.now())) {
      throw GovernanceCatalogException.invalid("Governance context expired");
    }
    return context.tenantId();
  }

  private static void validateRange(Instant from, Instant to) {
    if (from != null
        && to != null
        && (from.isAfter(to) || Duration.between(from, to).compareTo(MAX_AUDIT_RANGE) > 0)) {
      throw GovernanceAuditException.invalid("Audit range must be ordered and at most 31 days");
    }
  }

  private static void append(StringBuilder sql, List<Object> values, String column, String value) {
    if (value != null && !value.isBlank()) {
      if (value.length() > 96) {
        throw GovernanceCatalogException.invalid("Governance filter is too long");
      }
      sql.append(" AND ").append(column).append(" = ?");
      values.add(value);
    }
  }

  private Map<String, Object> tenant(ResultSet rs) throws SQLException {
    var value = new LinkedHashMap<String, Object>();
    value.put("id", rs.getString("id"));
    value.put("displayName", rs.getString("display_name"));
    value.put("slug", rs.getString("slug"));
    value.put("state", rs.getString("state"));
    value.put("policyVersion", rs.getString("policy_version"));
    value.put("revision", rs.getLong("revision"));
    value.put("createdAt", rs.getTimestamp("created_at").toInstant());
    value.put("updatedAt", rs.getTimestamp("updated_at").toInstant());
    return value;
  }

  private Map<String, Object> membership(ResultSet rs) throws SQLException {
    var value = new LinkedHashMap<String, Object>();
    value.put("id", rs.getString("id"));
    value.put("principalId", rs.getString("principal_id"));
    value.put("organizationId", rs.getString("organization_id"));
    value.put("roles", jsonValue(rs.getString("roles_json")));
    value.put("state", rs.getString("state"));
    value.put("policyVersion", rs.getString("policy_version"));
    value.put("revision", rs.getLong("revision"));
    value.put("createdAt", rs.getTimestamp("created_at").toInstant());
    value.put("updatedAt", rs.getTimestamp("updated_at").toInstant());
    return value;
  }

  private Map<String, Object> audit(ResultSet rs) throws SQLException {
    var value = new LinkedHashMap<String, Object>();
    value.put("id", rs.getString("id"));
    value.put("eventId", rs.getString("event_id"));
    value.put("principalId", rs.getString("principal_id"));
    value.put("action", rs.getString("action"));
    value.put("resourceType", rs.getString("resource_type"));
    value.put("resourceId", rs.getString("resource_id"));
    value.put("outcome", rs.getString("outcome"));
    value.put("requestId", rs.getString("request_id"));
    value.put("traceId", rs.getString("trace_id"));
    value.put("policyVersion", rs.getString("policy_version"));
    value.put("schemaVersion", rs.getString("schema_version"));
    value.put("occurredAt", rs.getTimestamp("occurred_at").toInstant());
    value.put("previousHash", rs.getString("previous_hash"));
    value.put("recordHash", rs.getString("record_hash"));
    value.put("summary", json(rs.getString("summary_json")));
    return value;
  }

  private Map<String, Object> trace(ResultSet rs) throws SQLException {
    var value = new LinkedHashMap<String, Object>();
    value.put("id", rs.getString("id"));
    value.put("traceId", rs.getString("trace_id"));
    value.put("requestId", rs.getString("request_id"));
    value.put("executionId", rs.getString("execution_id"));
    value.put("module", rs.getString("module"));
    value.put("operation", rs.getString("operation"));
    value.put("outcome", rs.getString("outcome"));
    value.put("durationMs", rs.getObject("duration_ms"));
    value.put("attributes", json(rs.getString("safe_attributes_json")));
    value.put("occurredAt", rs.getTimestamp("occurred_at").toInstant());
    return value;
  }

  private AuditVerificationRow verificationRow(ResultSet rs) throws SQLException {
    Map<String, Object> summary = json(rs.getString("summary_json"));
    var command =
        new AuditAppendCommand(
            UUID.fromString(rs.getString("event_id")),
            UUID.fromString(rs.getString("tenant_id")),
            UUID.fromString(rs.getString("principal_id")),
            rs.getString("action"),
            rs.getString("resource_type"),
            rs.getString("resource_id"),
            AuditOutcome.valueOf(rs.getString("outcome")),
            rs.getString("request_id"),
            rs.getString("trace_id"),
            rs.getString("policy_version"),
            rs.getString("schema_version"),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getTimestamp("retention_deadline") == null
                ? null
                : rs.getTimestamp("retention_deadline").toInstant(),
            summary);
    return new AuditVerificationRow(
        UUID.fromString(rs.getString("id")),
        command,
        rs.getString("previous_hash"),
        rs.getString("record_hash"),
        summary);
  }

  private Map<String, Object> json(String source) {
    try {
      return mapper.readValue(source, MAP_TYPE);
    } catch (JsonProcessingException exception) {
      throw GovernanceAuditException.integrity("Stored Governance JSON is invalid");
    }
  }

  private Object jsonValue(String source) {
    try {
      return mapper.readTree(source);
    } catch (JsonProcessingException exception) {
      throw GovernanceAuditException.integrity("Stored Governance JSON is invalid");
    }
  }

  private String canonical(Map<String, Object> value) {
    try {
      return mapper
          .writer()
          .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw GovernanceAuditException.integrity("Stored audit summary cannot be verified");
    }
  }

  private static int boundedLimit(int limit) {
    return Math.min(Math.max(limit, 1), 100);
  }

  private record AuditVerificationRow(
      UUID id,
      AuditAppendCommand command,
      String previousHash,
      String recordHash,
      Map<String, Object> summary) {}
}
