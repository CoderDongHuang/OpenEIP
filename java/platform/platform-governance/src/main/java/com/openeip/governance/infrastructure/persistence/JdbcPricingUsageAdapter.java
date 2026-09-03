package com.openeip.governance.infrastructure.persistence;

import com.openeip.governance.application.usage.PricingSnapshotPort;
import com.openeip.governance.application.usage.UsageLedgerPort;
import com.openeip.governance.domain.usage.PricingSnapshot;
import com.openeip.governance.domain.usage.PricingSnapshotRegistration;
import com.openeip.governance.domain.usage.UsageAppendResult;
import com.openeip.governance.domain.usage.UsageRecord;
import com.openeip.governance.domain.usage.UsageRegistration;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter for immutable pricing snapshots and idempotent usage cost records. */
@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JdbcTemplate is an application-scoped collaborator.")
public class JdbcPricingUsageAdapter implements PricingSnapshotPort, UsageLedgerPort {
  private final JdbcTemplate jdbc;

  public JdbcPricingUsageAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public PricingSnapshot create(PricingSnapshotRegistration registration) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO governance_pricing_snapshots
            (id, tenant_id, provider_id, model_id, version, input_unit_price,
             output_unit_price, currency, rounding_mode, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          id.toString(),
          registration.tenantId().toString(),
          registration.providerId().toString(),
          registration.modelId().toString(),
          registration.version(),
          registration.inputUnitPrice(),
          registration.outputUnitPrice(),
          registration.currency(),
          registration.roundingMode(),
          timestamp(registration.now()));
      return pricingSnapshot(registration.tenantId(), id).orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw GovernanceCatalogException.conflict("Pricing version already exists in this tenant");
    }
  }

  @Override
  public Optional<PricingSnapshot> pricingSnapshot(UUID tenantId, UUID pricingSnapshotId) {
    return jdbc
        .query(
            "SELECT * FROM governance_pricing_snapshots WHERE tenant_id = ? AND id = ?",
            (rs, row) -> pricingSnapshot(rs),
            tenantId.toString(),
            pricingSnapshotId.toString())
        .stream()
        .findFirst();
  }

  @Override
  @Transactional
  public UsageAppendResult append(
      UsageRegistration registration, PricingSnapshot pricing, BigDecimal calculatedAmount) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO governance_usage_records
            (id, tenant_id, execution_id, provider_request_id, usage_revision,
             pricing_snapshot_id, unit_type, input_units, output_units, currency,
             rounding_mode, calculated_amount, request_id, trace_id, source_ref, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          id.toString(),
          registration.tenantId().toString(),
          registration.executionId().toString(),
          registration.providerRequestId(),
          registration.usageRevision(),
          pricing.id().toString(),
          registration.unitType(),
          registration.inputUnits(),
          registration.outputUnits(),
          pricing.currency(),
          pricing.roundingMode(),
          calculatedAmount,
          registration.requestId(),
          registration.traceId(),
          registration.sourceRef(),
          timestamp(registration.now()));
      return new UsageAppendResult(usageById(registration.tenantId(), id).orElseThrow(), false);
    } catch (DuplicateKeyException exception) {
      UsageRecord existing =
          usageByIdempotency(
                  registration.tenantId(),
                  registration.executionId(),
                  registration.providerRequestId(),
                  registration.usageRevision())
              .orElseThrow(
                  () -> GovernanceCatalogException.conflict("Usage idempotency key is invalid"));
      if (!sameFact(existing, registration, pricing, calculatedAmount)) {
        throw GovernanceCatalogException.conflict("Usage idempotency key has different facts");
      }
      return new UsageAppendResult(existing, true);
    }
  }

  @Override
  public Optional<UsageRecord> usageByIdempotency(
      UUID tenantId, UUID executionId, String providerRequestId, long usageRevision) {
    return jdbc
        .query(
            """
            SELECT * FROM governance_usage_records
            WHERE tenant_id = ? AND execution_id = ?
              AND provider_request_id = ? AND usage_revision = ?
            """,
            (rs, row) -> usage(rs),
            tenantId.toString(),
            executionId.toString(),
            providerRequestId,
            usageRevision)
        .stream()
        .findFirst();
  }

  @Override
  public List<UsageRecord> usages(
      UUID tenantId, UUID executionId, Instant from, Instant to, int limit) {
    StringBuilder sql =
        new StringBuilder("SELECT * FROM governance_usage_records WHERE tenant_id = ?");
    List<Object> arguments = new ArrayList<>();
    arguments.add(tenantId.toString());
    if (executionId != null) {
      sql.append(" AND execution_id = ?");
      arguments.add(executionId.toString());
    }
    if (from != null) {
      sql.append(" AND created_at >= ?");
      arguments.add(timestamp(from));
    }
    if (to != null) {
      sql.append(" AND created_at < ?");
      arguments.add(timestamp(to));
    }
    sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
    arguments.add(limit);
    return jdbc.query(sql.toString(), (rs, row) -> usage(rs), arguments.toArray());
  }

  @Override
  public BigDecimal totalAmount(UUID tenantId, UUID executionId, Instant from, Instant to) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT COALESCE(SUM(calculated_amount), 0) FROM governance_usage_records");
    sql.append(" WHERE tenant_id = ?");
    List<Object> arguments = new ArrayList<>();
    arguments.add(tenantId.toString());
    if (executionId != null) {
      sql.append(" AND execution_id = ?");
      arguments.add(executionId.toString());
    }
    if (from != null) {
      sql.append(" AND created_at >= ?");
      arguments.add(timestamp(from));
    }
    if (to != null) {
      sql.append(" AND created_at < ?");
      arguments.add(timestamp(to));
    }
    BigDecimal result = jdbc.queryForObject(sql.toString(), BigDecimal.class, arguments.toArray());
    return result == null ? BigDecimal.ZERO : result;
  }

  private Optional<UsageRecord> usageById(UUID tenantId, UUID id) {
    return jdbc
        .query(
            "SELECT * FROM governance_usage_records WHERE tenant_id = ? AND id = ?",
            (rs, row) -> usage(rs),
            tenantId.toString(),
            id.toString())
        .stream()
        .findFirst();
  }

  private boolean sameFact(
      UsageRecord existing,
      UsageRegistration registration,
      PricingSnapshot pricing,
      BigDecimal calculatedAmount) {
    return existing.pricingSnapshotId().equals(pricing.id())
        && existing.unitType().equals(registration.unitType())
        && existing.inputUnits() == registration.inputUnits()
        && existing.outputUnits() == registration.outputUnits()
        && existing.currency().equals(pricing.currency())
        && existing.roundingMode().equals(pricing.roundingMode())
        && existing.calculatedAmount().compareTo(calculatedAmount) == 0
        && existing.requestId().equals(registration.requestId())
        && existing.traceId().equals(registration.traceId())
        && existing.sourceRef().equals(registration.sourceRef());
  }

  private PricingSnapshot pricingSnapshot(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new PricingSnapshot(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        UUID.fromString(rs.getString("provider_id")),
        UUID.fromString(rs.getString("model_id")),
        rs.getString("version"),
        rs.getBigDecimal("input_unit_price"),
        rs.getBigDecimal("output_unit_price"),
        rs.getString("currency"),
        rs.getString("rounding_mode"),
        rs.getTimestamp("created_at").toInstant());
  }

  private UsageRecord usage(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new UsageRecord(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        UUID.fromString(rs.getString("execution_id")),
        rs.getString("provider_request_id"),
        rs.getLong("usage_revision"),
        UUID.fromString(rs.getString("pricing_snapshot_id")),
        rs.getString("unit_type"),
        rs.getLong("input_units"),
        rs.getLong("output_units"),
        rs.getString("currency"),
        rs.getString("rounding_mode"),
        rs.getBigDecimal("calculated_amount"),
        rs.getString("request_id"),
        rs.getString("trace_id"),
        rs.getString("source_ref"),
        rs.getTimestamp("created_at").toInstant());
  }

  private static Timestamp timestamp(Instant value) {
    return Timestamp.from(value);
  }
}
