package com.openeip.governance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.application.quota.QuotaPolicyService;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.domain.quota.QuotaLimits;
import com.openeip.governance.domain.quota.QuotaPolicyRegistration;
import com.openeip.governance.domain.quota.QuotaPolicyUpdate;
import com.openeip.governance.domain.quota.QuotaWindowType;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class JdbcQuotaPolicyAdapterTest {
  private static final String TENANT_ONE = "11111111-1111-4111-8111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-4222-8222-222222222222";
  private static final UUID PRINCIPAL = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final String REQUEST_ID = "request-quota-1";
  private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
  private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
  private JdbcTemplate jdbc;
  private JdbcQuotaPolicyAdapter adapter;
  private QuotaPolicyService service;
  private UUID quotaId;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:governance-quota-"
            + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(dataSource);
    String migration =
        new ClassPathResource("db/migration/V2.7.0__init_governance_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    try (var connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection,
          new ByteArrayResource(h2Compatible(migration).getBytes(StandardCharsets.UTF_8)));
    }
    insertTenant(TENANT_ONE, "tenant-one");
    insertTenant(TENANT_TWO, "tenant-two");
    var audit = new AuditService(new JdbcAuditOutboxAdapter(jdbc, new ObjectMapper()));
    adapter = new JdbcQuotaPolicyAdapter(jdbc);
    service = new QuotaPolicyService(adapter, audit);
    bind(TENANT_ONE);
    quotaId = service.create(registration("quota-one", limits())).id();
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void persistsAllQuotaLimitsAndSupportsTenantScopedCrud() {
    var quota = service.get(quotaId);

    assertThat(quota.name()).isEqualTo("quota-one");
    assertThat(quota.policyVersion()).isEqualTo("policy-1");
    assertThat(quota.limits()).isEqualTo(limits());
    assertThat(quota.windowType()).isEqualTo(QuotaWindowType.DAILY);
    assertThat(service.list(20)).extracting(value -> value.id()).containsExactly(quotaId);

    bind(TENANT_TWO);
    assertThat(adapter.quota(UUID.fromString(TENANT_TWO), quotaId)).isEmpty();
    assertThatThrownBy(() -> service.get(quotaId))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-V-001"));
  }

  @Test
  void updatesWithExpectedRevisionAndRejectsStaleRevision() {
    var update =
        new QuotaPolicyUpdate(
            UUID.fromString(TENANT_ONE),
            quotaId,
            "quota-renamed",
            new QuotaLimits(2000L, new BigDecimal("20.000000"), 200L, 8),
            QuotaWindowType.WEEKLY,
            0,
            NOW.plusSeconds(1));
    var updated = service.update(update);

    assertThat(updated.revision()).isEqualTo(1);
    assertThat(updated.name()).isEqualTo("quota-renamed");
    assertThat(updated.windowType()).isEqualTo(QuotaWindowType.WEEKLY);
    assertThatThrownBy(() -> service.update(update))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-001"));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_quota_policies", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void rejectsDuplicateNamesAndInvalidLimitDefinitions() {
    assertThatThrownBy(() -> service.create(registration("quota-one", limits())))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-001"));
    assertThatThrownBy(() -> new QuotaLimits(null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> {
              QuotaLimits invalid = new QuotaLimits(-1L, null, null, null);
              service.create(registration("quota-invalid", invalid));
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  private QuotaPolicyRegistration registration(String name, QuotaLimits limits) {
    return new QuotaPolicyRegistration(
        UUID.fromString(TENANT_ONE), name, limits, QuotaWindowType.DAILY, NOW);
  }

  private QuotaLimits limits() {
    return new QuotaLimits(1000L, new BigDecimal("10.000000"), 100L, 4);
  }

  private void bind(String tenantId) {
    TenantContextHolder.bind(
        new TenantContext(
            UUID.fromString(tenantId),
            null,
            PRINCIPAL,
            UUID.randomUUID(),
            Set.of("ROLE_OPERATOR"),
            "policy-1",
            REQUEST_ID,
            TRACE_ID,
            GovernanceScope.TENANT,
            Instant.parse("2099-01-01T00:00:00Z")));
  }

  private void insertTenant(String id, String slug) {
    jdbc.update(
        """
        INSERT INTO governance_tenants
          (id, tenant_id, display_name, slug, state, policy_version, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        id,
        slug,
        slug);
  }

  private static String h2Compatible(String mysql) {
    String transformed =
        mysql
            .replaceAll("(?m)^\\s*KEY [^\\r\\n]+,?\\r?\\n", "")
            .replaceAll("UNIQUE KEY ([A-Za-z0-9_]+) \\(", "CONSTRAINT $1 UNIQUE (")
            .replaceAll(
                "\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;", ");");
    return transformed.replaceAll(",\\s*\\);", "\\n);");
  }
}
