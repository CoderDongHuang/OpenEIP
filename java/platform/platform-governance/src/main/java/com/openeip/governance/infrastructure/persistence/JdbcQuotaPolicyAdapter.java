package com.openeip.governance.infrastructure.persistence;

import com.openeip.governance.application.quota.QuotaPolicyPort;
import com.openeip.governance.domain.quota.QuotaLimits;
import com.openeip.governance.domain.quota.QuotaPolicy;
import com.openeip.governance.domain.quota.QuotaPolicyRegistration;
import com.openeip.governance.domain.quota.QuotaPolicyUpdate;
import com.openeip.governance.domain.quota.QuotaWindowType;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter for revisioned tenant quota policies. */
@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JdbcTemplate is an application-scoped collaborator.")
public class JdbcQuotaPolicyAdapter implements QuotaPolicyPort {
  private final JdbcTemplate jdbc;

  public JdbcQuotaPolicyAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public QuotaPolicy create(QuotaPolicyRegistration registration, String policyVersion) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO governance_quota_policies
            (id, tenant_id, name, policy_version, token_limit, cost_limit, request_limit,
             concurrency_limit, window_type, revision, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
          """,
          id.toString(),
          registration.tenantId().toString(),
          registration.name(),
          policyVersion,
          registration.limits().tokenLimit(),
          registration.limits().costLimit(),
          registration.limits().requestLimit(),
          registration.limits().concurrencyLimit(),
          registration.windowType().name(),
          timestamp(registration.now()),
          timestamp(registration.now()));
      return quota(registration.tenantId(), id).orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw GovernanceCatalogException.conflict("Quota policy name already exists in this tenant");
    }
  }

  @Override
  public Optional<QuotaPolicy> quota(UUID tenantId, UUID quotaPolicyId) {
    return jdbc
        .query(
            "SELECT * FROM governance_quota_policies WHERE tenant_id = ? AND id = ?",
            (rs, row) -> quota(rs),
            tenantId.toString(),
            quotaPolicyId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public List<QuotaPolicy> quotas(UUID tenantId, int limit) {
    return jdbc.query(
        """
        SELECT * FROM governance_quota_policies
        WHERE tenant_id = ?
        ORDER BY updated_at DESC, id DESC
        LIMIT ?
        """,
        (rs, row) -> quota(rs),
        tenantId.toString(),
        limit);
  }

  @Override
  @Transactional
  public boolean update(QuotaPolicyUpdate update, String policyVersion) {
    try {
      return jdbc.update(
              """
              UPDATE governance_quota_policies
              SET name = ?, policy_version = ?, token_limit = ?, cost_limit = ?,
                  request_limit = ?, concurrency_limit = ?, window_type = ?,
                  revision = revision + 1, updated_at = ?
              WHERE tenant_id = ? AND id = ? AND revision = ?
              """,
              update.name(),
              policyVersion,
              update.limits().tokenLimit(),
              update.limits().costLimit(),
              update.limits().requestLimit(),
              update.limits().concurrencyLimit(),
              update.windowType().name(),
              timestamp(update.now()),
              update.tenantId().toString(),
              update.quotaPolicyId().toString(),
              update.expectedRevision())
          == 1;
    } catch (DuplicateKeyException exception) {
      throw GovernanceCatalogException.conflict("Quota policy name already exists in this tenant");
    }
  }

  private QuotaPolicy quota(java.sql.ResultSet rs) throws java.sql.SQLException {
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

  private static Long nullableLong(java.sql.ResultSet rs, String column)
      throws java.sql.SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static Integer nullableInteger(java.sql.ResultSet rs, String column)
      throws java.sql.SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  private static Timestamp timestamp(Instant value) {
    return Timestamp.from(value);
  }
}
