package com.openeip.governance.infrastructure.persistence;

import com.openeip.governance.application.quota.QuotaEnforcementPort;
import com.openeip.governance.domain.quota.QuotaConsumption;
import com.openeip.governance.domain.quota.QuotaLimits;
import com.openeip.governance.domain.quota.QuotaPolicy;
import com.openeip.governance.domain.quota.QuotaReservation;
import com.openeip.governance.domain.quota.QuotaReservationRegistration;
import com.openeip.governance.domain.quota.QuotaWindow;
import com.openeip.governance.domain.quota.QuotaWindowType;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC implementation of serialized, tenant-scoped runtime quota admission state. */
@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JdbcTemplate is an application-scoped collaborator.")
public class JdbcQuotaEnforcementAdapter implements QuotaEnforcementPort {
  private final JdbcTemplate jdbc;

  public JdbcQuotaEnforcementAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<QuotaPolicy> lockPolicy(UUID tenantId, UUID quotaPolicyId) {
    return jdbc
        .query(
            """
            SELECT * FROM governance_quota_policies
            WHERE tenant_id = ? AND id = ?
            FOR UPDATE
            """,
            (rs, row) -> policy(rs),
            tenantId.toString(),
            quotaPolicyId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<QuotaReservation> reservationByIdempotency(
      UUID tenantId, UUID quotaPolicyId, String idempotencyKey) {
    return jdbc
        .query(
            """
            SELECT * FROM governance_quota_reservations
            WHERE tenant_id = ? AND quota_policy_id = ? AND idempotency_key = ?
            """,
            (rs, row) -> reservation(rs),
            tenantId.toString(),
            quotaPolicyId.toString(),
            idempotencyKey)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<QuotaReservation> reservation(UUID tenantId, UUID reservationId) {
    return jdbc
        .query(
            "SELECT * FROM governance_quota_reservations WHERE tenant_id = ? AND id = ?",
            (rs, row) -> reservation(rs),
            tenantId.toString(),
            reservationId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public QuotaConsumption consumption(
      UUID tenantId, UUID quotaPolicyId, UUID executionId, QuotaWindow window, Instant now) {
    boolean executionWindow = window.type() == QuotaWindowType.EXECUTION;
    BigDecimal usageTokens = usageTotal(tenantId, executionId, window, executionWindow, false);
    BigDecimal usageCost = usageTotal(tenantId, executionId, window, executionWindow, true);

    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              COALESCE(SUM(CASE
                WHEN decision = 'ALLOW' AND released_at IS NULL AND expires_at > ?
                THEN requested_token_units ELSE 0 END), 0) AS active_tokens,
              COALESCE(SUM(CASE
                WHEN decision = 'ALLOW' AND released_at IS NULL AND expires_at > ?
                THEN requested_cost_amount ELSE 0 END), 0) AS active_cost,
              COALESCE(SUM(CASE
                WHEN decision = 'ALLOW' THEN requested_request_units ELSE 0 END), 0) AS requests,
              COALESCE(SUM(CASE
                WHEN decision = 'ALLOW' AND released_at IS NULL AND expires_at > ?
                THEN requested_concurrency_units ELSE 0 END), 0) AS active_concurrency
            FROM governance_quota_reservations
            WHERE tenant_id = ? AND quota_policy_id = ?
              AND window_start = ? AND window_end = ?
            """);
    List<Object> arguments = new ArrayList<>();
    arguments.add(timestamp(now));
    arguments.add(timestamp(now));
    arguments.add(timestamp(now));
    arguments.add(tenantId.toString());
    arguments.add(quotaPolicyId.toString());
    arguments.add(timestamp(window.start()));
    arguments.add(timestamp(window.end()));
    if (executionWindow) {
      sql.append(" AND execution_id = ?");
      arguments.add(executionId.toString());
    }
    return jdbc.queryForObject(
        sql.toString(),
        (rs, row) ->
            new QuotaConsumption(
                boundedLong(usageTokens.add(rs.getBigDecimal("active_tokens")), "token"),
                usageCost.add(rs.getBigDecimal("active_cost")),
                boundedLong(rs.getBigDecimal("requests"), "request"),
                boundedInteger(rs.getBigDecimal("active_concurrency"), "concurrency")),
        arguments.toArray());
  }

  @Override
  public QuotaReservation append(QuotaReservationRegistration registration) {
    UUID id = UUID.randomUUID();
    var request = registration.request();
    try {
      jdbc.update(
          """
          INSERT INTO governance_quota_reservations
            (id, tenant_id, quota_policy_id, execution_id, idempotency_key, policy_version,
             window_type, window_start, window_end, requested_token_units,
             requested_cost_amount, requested_request_units, requested_concurrency_units,
             observed_token_units, observed_cost_amount, observed_request_units,
             observed_concurrency_units, decision, request_id, trace_id, expires_at, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          id.toString(),
          request.tenantId().toString(),
          request.quotaPolicyId().toString(),
          request.executionId().toString(),
          request.idempotencyKey(),
          registration.policyVersion(),
          registration.window().type().name(),
          timestamp(registration.window().start()),
          timestamp(registration.window().end()),
          request.requestedTokens(),
          request.requestedCost(),
          request.concurrencyUnits(),
          registration.observed().tokenUnits(),
          registration.observed().costAmount(),
          registration.observed().requestUnits(),
          registration.observed().concurrencyUnits(),
          registration.decision().name(),
          registration.requestId(),
          registration.traceId(),
          timestamp(request.expiresAt()),
          timestamp(registration.admittedAt()));
      return reservation(request.tenantId(), id).orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw GovernanceCatalogException.idempotency(
          "Quota admission idempotency key already exists");
    }
  }

  @Override
  public boolean release(UUID tenantId, UUID reservationId, Instant releasedAt) {
    return jdbc.update(
            """
            UPDATE governance_quota_reservations
            SET released_at = ?
            WHERE tenant_id = ? AND id = ? AND decision = 'ALLOW' AND released_at IS NULL
            """,
            timestamp(releasedAt),
            tenantId.toString(),
            reservationId.toString())
        == 1;
  }

  private BigDecimal usageTotal(
      UUID tenantId, UUID executionId, QuotaWindow window, boolean executionWindow, boolean cost) {
    String aggregateExpression =
        cost
            ? "COALESCE(SUM(calculated_amount), 0)"
            : "COALESCE(SUM(input_units), 0) + COALESCE(SUM(output_units), 0)";
    StringBuilder sql =
        new StringBuilder(
            "SELECT " + aggregateExpression + " FROM governance_usage_records WHERE tenant_id = ?");
    List<Object> arguments = new ArrayList<>();
    arguments.add(tenantId.toString());
    if (executionWindow) {
      sql.append(" AND execution_id = ?");
      arguments.add(executionId.toString());
    } else {
      sql.append(" AND created_at >= ? AND created_at < ?");
      arguments.add(timestamp(window.start()));
      arguments.add(timestamp(window.end()));
    }
    BigDecimal value = jdbc.queryForObject(sql.toString(), BigDecimal.class, arguments.toArray());
    return value == null ? BigDecimal.ZERO : value;
  }

  private QuotaPolicy policy(ResultSet rs) throws SQLException {
    return new QuotaPolicy(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        rs.getString("name"),
        rs.getString("policy_version"),
        new QuotaLimits(
            nullableLong(rs, "token_limit"),
            rs.getBigDecimal("cost_limit"),
            nullableLong(rs, "request_limit"),
            nullableInteger(rs, "concurrency_limit")),
        QuotaWindowType.valueOf(rs.getString("window_type")),
        rs.getLong("revision"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private QuotaReservation reservation(ResultSet rs) throws SQLException {
    return new QuotaReservation(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        UUID.fromString(rs.getString("quota_policy_id")),
        UUID.fromString(rs.getString("execution_id")),
        rs.getString("idempotency_key"),
        rs.getString("policy_version"),
        new QuotaWindow(
            QuotaWindowType.valueOf(rs.getString("window_type")),
            rs.getTimestamp("window_start").toInstant(),
            rs.getTimestamp("window_end").toInstant()),
        rs.getLong("requested_token_units"),
        rs.getBigDecimal("requested_cost_amount"),
        rs.getInt("requested_concurrency_units"),
        new QuotaConsumption(
            rs.getLong("observed_token_units"),
            rs.getBigDecimal("observed_cost_amount"),
            rs.getLong("observed_request_units"),
            rs.getInt("observed_concurrency_units")),
        com.openeip.governance.domain.quota.QuotaDecisionOutcome.valueOf(rs.getString("decision")),
        rs.getString("request_id"),
        rs.getString("trace_id"),
        rs.getTimestamp("expires_at").toInstant(),
        instant(rs, "released_at"),
        rs.getTimestamp("created_at").toInstant());
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private static long boundedLong(BigDecimal value, String dimension) {
    try {
      return value.longValueExact();
    } catch (ArithmeticException exception) {
      throw GovernanceCatalogException.invalid(
          "Persisted " + dimension + " quota exceeds supported bounds");
    }
  }

  private static int boundedInteger(BigDecimal value, String dimension) {
    try {
      return value.intValueExact();
    } catch (ArithmeticException exception) {
      throw GovernanceCatalogException.invalid(
          "Persisted " + dimension + " quota exceeds supported bounds");
    }
  }

  private static Timestamp timestamp(Instant value) {
    return Timestamp.from(value);
  }
}
