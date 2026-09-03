package com.openeip.governance.infrastructure.persistence;

import com.openeip.governance.application.budget.BudgetAlertPort;
import com.openeip.governance.application.budget.BudgetPort;
import com.openeip.governance.domain.budget.Budget;
import com.openeip.governance.domain.budget.BudgetAlert;
import com.openeip.governance.domain.budget.BudgetAlertRegistration;
import com.openeip.governance.domain.budget.BudgetAlertResult;
import com.openeip.governance.domain.budget.BudgetAlertStatus;
import com.openeip.governance.domain.budget.BudgetDecision;
import com.openeip.governance.domain.budget.BudgetDecisionOutcome;
import com.openeip.governance.domain.budget.BudgetDecisionRegistration;
import com.openeip.governance.domain.budget.BudgetDecisionType;
import com.openeip.governance.domain.budget.BudgetRegistration;
import com.openeip.governance.domain.budget.BudgetWindowType;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter for tenant budgets, append-only decisions, and idempotent alerts. */
@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JdbcTemplate is an application-scoped collaborator.")
public class JdbcBudgetAlertAdapter implements BudgetPort, BudgetAlertPort {
  private final JdbcTemplate jdbc;

  public JdbcBudgetAlertAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public Budget create(BudgetRegistration registration) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO governance_budgets
            (id, tenant_id, name, currency, limit_amount, window_type, revision,
             created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
          """,
          id.toString(),
          registration.tenantId().toString(),
          registration.name(),
          registration.currency(),
          registration.limitAmount(),
          registration.windowType().name(),
          timestamp(registration.now()),
          timestamp(registration.now()));
      return budget(registration.tenantId(), id).orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw GovernanceCatalogException.conflict("Budget name already exists in this tenant");
    }
  }

  @Override
  public Optional<Budget> budget(UUID tenantId, UUID budgetId) {
    return jdbc
        .query(
            "SELECT * FROM governance_budgets WHERE tenant_id = ? AND id = ?",
            (rs, row) -> budget(rs),
            tenantId.toString(),
            budgetId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public Budget lockBudget(UUID tenantId, UUID budgetId) {
    return jdbc
        .query(
            "SELECT * FROM governance_budgets WHERE tenant_id = ? AND id = ? FOR UPDATE",
            (rs, row) -> budget(rs),
            tenantId.toString(),
            budgetId.toString())
        .stream()
        .findFirst()
        .orElseThrow(
            () -> GovernanceCatalogException.invalid("Budget was not found in this tenant"));
  }

  @Override
  public List<Budget> budgets(UUID tenantId, int limit) {
    return jdbc.query(
        "SELECT * FROM governance_budgets WHERE tenant_id = ? ORDER BY updated_at DESC, id DESC LIMIT ?",
        (rs, row) -> budget(rs),
        tenantId.toString(),
        limit);
  }

  @Override
  public Optional<BudgetDecision> latestDecision(UUID tenantId, UUID budgetId, UUID executionId) {
    return jdbc
        .query(
            """
            SELECT * FROM governance_budget_decisions
            WHERE tenant_id = ? AND budget_id = ? AND execution_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """,
            (rs, row) -> decision(rs),
            tenantId.toString(),
            budgetId.toString(),
            executionId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public List<BudgetDecision> latestAllowedDecisions(
      UUID tenantId, UUID budgetId, Instant from, Instant to) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT d.* FROM governance_budget_decisions d
            WHERE d.tenant_id = ? AND d.budget_id = ? AND d.decision = 'ALLOW'
              AND NOT EXISTS (
                SELECT 1 FROM governance_budget_decisions newer
                WHERE newer.tenant_id = d.tenant_id AND newer.budget_id = d.budget_id
                  AND newer.execution_id = d.execution_id
                  AND (newer.created_at > d.created_at
                       OR (newer.created_at = d.created_at AND newer.id > d.id))
                  AND newer.created_at IS NOT NULL
              )
            """);
    java.util.ArrayList<Object> arguments = new java.util.ArrayList<>();
    arguments.add(tenantId.toString());
    arguments.add(budgetId.toString());
    if (from != null) {
      sql.append(" AND d.created_at >= ?");
      arguments.add(timestamp(from));
    }
    if (to != null) {
      sql.append(" AND d.created_at < ?");
      arguments.add(timestamp(to));
    }
    sql.append(" ORDER BY d.created_at DESC, d.id DESC");
    return jdbc.query(sql.toString(), (rs, row) -> decision(rs), arguments.toArray());
  }

  @Override
  @Transactional
  public BudgetDecision appendDecision(BudgetDecisionRegistration registration) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO governance_budget_decisions
          (id, tenant_id, budget_id, execution_id, decision_type, policy_version,
           observed_amount, reserved_amount, decision, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id.toString(),
        registration.tenantId().toString(),
        registration.budgetId().toString(),
        registration.executionId().toString(),
        registration.type().name(),
        registration.policyVersion(),
        registration.observedAmount(),
        registration.reservedAmount(),
        registration.decision().name(),
        timestamp(registration.createdAt()));
    return jdbc
        .query(
            "SELECT * FROM governance_budget_decisions WHERE tenant_id = ? AND id = ?",
            (rs, row) -> decision(rs),
            registration.tenantId().toString(),
            id.toString())
        .stream()
        .findFirst()
        .orElseThrow();
  }

  @Override
  @Transactional
  public BudgetAlertResult create(BudgetAlertRegistration registration) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO governance_alerts
            (id, tenant_id, budget_id, window_start, threshold, crossing_revision,
             status, created_at)
          VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
          """,
          id.toString(),
          registration.tenantId().toString(),
          registration.budgetId().toString(),
          timestamp(registration.windowStart()),
          registration.threshold(),
          registration.crossingRevision(),
          timestamp(registration.now()));
      return new BudgetAlertResult(alert(registration.tenantId(), id).orElseThrow(), false);
    } catch (DuplicateKeyException exception) {
      BudgetAlert existing =
          alertByIdempotency(
                  registration.tenantId(),
                  registration.budgetId(),
                  registration.windowStart(),
                  registration.threshold(),
                  registration.crossingRevision())
              .orElseThrow(
                  () ->
                      GovernanceCatalogException.conflict(
                          "Budget alert idempotency key is invalid"));
      return new BudgetAlertResult(existing, true);
    }
  }

  @Override
  public Optional<BudgetAlert> alert(UUID tenantId, UUID alertId) {
    return jdbc
        .query(
            "SELECT * FROM governance_alerts WHERE tenant_id = ? AND id = ?",
            (rs, row) -> alert(rs),
            tenantId.toString(),
            alertId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<BudgetAlert> alertByIdempotency(
      UUID tenantId,
      UUID budgetId,
      Instant windowStart,
      BigDecimal threshold,
      long crossingRevision) {
    return jdbc
        .query(
            """
            SELECT * FROM governance_alerts
            WHERE tenant_id = ? AND budget_id = ? AND window_start = ?
              AND threshold = ? AND crossing_revision = ?
            """,
            (rs, row) -> alert(rs),
            tenantId.toString(),
            budgetId.toString(),
            timestamp(windowStart),
            threshold,
            crossingRevision)
        .stream()
        .findFirst();
  }

  @Override
  public boolean updateStatus(
      UUID tenantId, UUID alertId, BudgetAlertStatus expectedStatus, BudgetAlertStatus newStatus) {
    return jdbc.update(
            """
            UPDATE governance_alerts SET status = ?
            WHERE tenant_id = ? AND id = ? AND status = ?
            """,
            newStatus.name(),
            tenantId.toString(),
            alertId.toString(),
            expectedStatus.name())
        == 1;
  }

  private Budget budget(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new Budget(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        rs.getString("name"),
        rs.getString("currency"),
        rs.getBigDecimal("limit_amount"),
        BudgetWindowType.valueOf(rs.getString("window_type")),
        rs.getLong("revision"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private BudgetDecision decision(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new BudgetDecision(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        UUID.fromString(rs.getString("budget_id")),
        UUID.fromString(rs.getString("execution_id")),
        BudgetDecisionType.valueOf(rs.getString("decision_type")),
        rs.getString("policy_version"),
        rs.getBigDecimal("observed_amount"),
        rs.getBigDecimal("reserved_amount"),
        BudgetDecisionOutcome.valueOf(rs.getString("decision")),
        rs.getTimestamp("created_at").toInstant());
  }

  private BudgetAlert alert(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new BudgetAlert(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        UUID.fromString(rs.getString("budget_id")),
        rs.getTimestamp("window_start").toInstant(),
        rs.getBigDecimal("threshold"),
        rs.getLong("crossing_revision"),
        BudgetAlertStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant());
  }

  private static Timestamp timestamp(Instant value) {
    return Timestamp.from(value);
  }
}
