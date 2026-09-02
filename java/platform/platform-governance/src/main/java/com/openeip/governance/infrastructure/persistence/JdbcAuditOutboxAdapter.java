package com.openeip.governance.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openeip.governance.application.audit.AuditAppendPort;
import com.openeip.governance.application.audit.AuditOutboxPort;
import com.openeip.governance.domain.audit.AuditAppendCommand;
import com.openeip.governance.domain.audit.AuditAppendResult;
import com.openeip.governance.domain.audit.AuditIntegrity;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.audit.AuditRecord;
import com.openeip.governance.domain.audit.OutboxEntry;
import com.openeip.governance.domain.audit.OutboxStatus;
import com.openeip.governance.shared.exception.GovernanceAuditException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** MySQL persistence adapter for atomic audit records and their transactional outbox. */
@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JdbcTemplate and ObjectMapper are application-scoped collaborators.")
public class JdbcAuditOutboxAdapter implements AuditAppendPort, AuditOutboxPort {
  private static final String EVENT_TYPE = "governance.audit.appended";
  private static final int EVENT_VERSION = 1;
  private static final TypeReference<Map<String, Object>> SUMMARY_TYPE = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcAuditOutboxAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public AuditAppendResult append(AuditAppendCommand command) {
    lockTenant(command.tenantId());
    Optional<AuditRecord> existing = findByEventId(command.tenantId(), command.eventId());
    if (existing.isPresent()) {
      return duplicateOrConflict(command, existing.orElseThrow());
    }

    String previousHash = previousHash(command.tenantId());
    String summaryJson = summaryJson(command.summary());
    String recordHash = AuditIntegrity.recordHash(command, previousHash, summaryJson);
    AuditRecord record =
        new AuditRecord(
            UUID.randomUUID(),
            command.eventId(),
            command.tenantId(),
            command.principalId(),
            command.action(),
            command.resourceType(),
            command.resourceId(),
            command.outcome(),
            command.requestId(),
            command.traceId(),
            command.policyVersion(),
            command.schemaVersion(),
            command.occurredAt(),
            previousHash,
            recordHash,
            command.retentionDeadline(),
            command.summary());
    jdbc.update(
        """
        INSERT INTO governance_audit_records
          (id, tenant_id, event_id, principal_id, action, resource_type, resource_id, outcome,
           request_id, trace_id, policy_version, schema_version, occurred_at, previous_hash,
           record_hash, summary_json, retention_deadline)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        record.id().toString(),
        record.tenantId().toString(),
        record.eventId().toString(),
        record.principalId().toString(),
        record.action(),
        record.resourceType(),
        record.resourceId(),
        record.outcome().name(),
        record.requestId(),
        record.traceId(),
        record.policyVersion(),
        record.schemaVersion(),
        timestamp(record.occurredAt()),
        record.previousHash(),
        record.recordHash(),
        summaryJson,
        timestamp(record.retentionDeadline()));
    jdbc.update(
        """
        INSERT INTO governance_outbox
          (id, tenant_id, aggregate_id, event_id, event_type, payload_json, status, attempts, created_at)
        VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?)
        """,
        UUID.randomUUID().toString(),
        record.tenantId().toString(),
        record.id().toString(),
        record.eventId().toString(),
        EVENT_TYPE,
        eventPayload(record, summaryJson),
        timestamp(record.occurredAt()));
    return new AuditAppendResult(record, false);
  }

  @Override
  @Transactional
  public List<OutboxEntry> claimPending(UUID tenantId, int limit) {
    if (tenantId == null || limit < 1 || limit > 100) {
      throw new IllegalArgumentException("tenantId and bounded positive limit are required");
    }
    List<OutboxEntry> entries =
        jdbc.query(
            """
            SELECT id, tenant_id, aggregate_id, event_id, event_type, payload_json, status,
                   attempts, created_at, delivered_at
            FROM governance_outbox
            WHERE tenant_id = ? AND status = 'PENDING'
            ORDER BY created_at, id
            LIMIT ? FOR UPDATE
            """,
            (rs, row) -> outbox(rs),
            tenantId.toString(),
            limit);
    for (OutboxEntry entry : entries) {
      jdbc.update(
          """
          UPDATE governance_outbox
          SET attempts = attempts + 1
          WHERE tenant_id = ? AND event_id = ? AND status = 'PENDING'
          """,
          tenantId.toString(),
          entry.eventId().toString());
    }
    return entries.stream()
        .map(
            entry ->
                new OutboxEntry(
                    entry.id(),
                    entry.tenantId(),
                    entry.aggregateId(),
                    entry.eventId(),
                    entry.eventType(),
                    entry.payloadJson(),
                    entry.status(),
                    entry.attempts() + 1,
                    entry.createdAt(),
                    entry.deliveredAt()))
        .toList();
  }

  @Override
  @Transactional
  public boolean markDelivered(UUID tenantId, UUID eventId) {
    return jdbc.update(
            """
            UPDATE governance_outbox
            SET status = 'DELIVERED', delivered_at = CURRENT_TIMESTAMP(6)
            WHERE tenant_id = ? AND event_id = ? AND status = 'PENDING'
            """,
            string(tenantId),
            string(eventId))
        == 1;
  }

  @Override
  @Transactional
  public boolean markRetryable(UUID tenantId, UUID eventId) {
    return jdbc.update(
            """
            UPDATE governance_outbox
            SET status = 'PENDING'
            WHERE tenant_id = ? AND event_id = ? AND status = 'PENDING'
            """,
            string(tenantId),
            string(eventId))
        == 1;
  }

  @Override
  @Transactional
  public boolean quarantine(UUID tenantId, UUID eventId) {
    return jdbc.update(
            """
            UPDATE governance_outbox
            SET status = 'QUARANTINED'
            WHERE tenant_id = ? AND event_id = ? AND status = 'PENDING'
            """,
            string(tenantId),
            string(eventId))
        == 1;
  }

  private AuditAppendResult duplicateOrConflict(AuditAppendCommand command, AuditRecord existing) {
    String expected =
        AuditIntegrity.recordHash(command, existing.previousHash(), summaryJson(command.summary()));
    if (!expected.equals(existing.recordHash())) {
      throw GovernanceAuditException.conflict("Event identifier collision");
    }
    return new AuditAppendResult(existing, true);
  }

  private void lockTenant(UUID tenantId) {
    if (jdbc.query(
                "SELECT id FROM governance_tenants WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getString(1),
                string(tenantId))
            .size()
        != 1) {
      throw GovernanceAuditException.integrity("Audit tenant does not exist");
    }
  }

  private String previousHash(UUID tenantId) {
    return jdbc
        .query(
            """
            SELECT record_hash FROM governance_audit_records
            WHERE tenant_id = ?
            ORDER BY occurred_at DESC, id DESC LIMIT 1 FOR UPDATE
            """,
            (rs, row) -> rs.getString(1),
            string(tenantId))
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Optional<AuditRecord> findByEventId(UUID tenantId, UUID eventId) {
    return jdbc
        .query(
            "SELECT * FROM governance_audit_records WHERE tenant_id = ? AND event_id = ?",
            (rs, row) -> audit(rs),
            string(tenantId),
            string(eventId))
        .stream()
        .findFirst();
  }

  private String summaryJson(Map<String, Object> summary) {
    try {
      return mapper
          .writer()
          .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(summary);
    } catch (JsonProcessingException exception) {
      throw GovernanceAuditException.invalid("Audit summary cannot be serialized");
    }
  }

  private String eventPayload(AuditRecord record, String summaryJson) {
    try {
      var data = new LinkedHashMap<String, Object>();
      data.put("resourceType", record.resourceType());
      data.put("resourceId", record.resourceId());
      data.put("outcome", record.outcome().name());
      data.put("summary", mapper.readTree(summaryJson));
      var payload = new LinkedHashMap<String, Object>();
      payload.put("eventId", record.eventId());
      payload.put("eventType", EVENT_TYPE);
      payload.put("eventVersion", EVENT_VERSION);
      payload.put("tenantId", record.tenantId());
      payload.put("requestId", record.requestId());
      payload.put("traceId", record.traceId());
      payload.put("policyVersion", record.policyVersion());
      payload.put("timestamp", record.occurredAt().toString());
      payload.put("data", data);
      return mapper
          .writer()
          .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw GovernanceAuditException.invalid("Audit event cannot be serialized");
    }
  }

  private AuditRecord audit(java.sql.ResultSet rs) throws java.sql.SQLException {
    try {
      return new AuditRecord(
          UUID.fromString(rs.getString("id")),
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
          rs.getString("previous_hash"),
          rs.getString("record_hash"),
          nullableInstant(rs.getTimestamp("retention_deadline")),
          mapper.readValue(rs.getString("summary_json"), SUMMARY_TYPE));
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw GovernanceAuditException.integrity("Stored audit record is invalid");
    }
  }

  private OutboxEntry outbox(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new OutboxEntry(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        UUID.fromString(rs.getString("aggregate_id")),
        UUID.fromString(rs.getString("event_id")),
        rs.getString("event_type"),
        rs.getString("payload_json"),
        OutboxStatus.valueOf(rs.getString("status")),
        rs.getInt("attempts"),
        rs.getTimestamp("created_at").toInstant(),
        nullableInstant(rs.getTimestamp("delivered_at")));
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant nullableInstant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private static String string(UUID value) {
    return value == null ? null : value.toString();
  }
}
