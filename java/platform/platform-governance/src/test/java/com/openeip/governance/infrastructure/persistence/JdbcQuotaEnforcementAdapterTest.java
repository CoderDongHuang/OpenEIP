package com.openeip.governance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.application.quota.QuotaEnforcementService;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.domain.quota.QuotaAdmissionRequest;
import com.openeip.governance.domain.quota.QuotaLimits;
import com.openeip.governance.domain.quota.QuotaWindow;
import com.openeip.governance.domain.quota.QuotaWindowType;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcQuotaEnforcementAdapterTest {
  private static final String TENANT_ONE = "11111111-1111-4111-8111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-4222-8222-222222222222";
  private static final UUID PRINCIPAL = UUID.fromString("33333333-3333-4333-8333-333333333333");

  private JdbcTemplate jdbc;
  private JdbcQuotaEnforcementAdapter adapter;
  private QuotaEnforcementService service;
  private TransactionTemplate transactions;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:governance-quota-enforcement-"
            + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000");
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
    adapter = new JdbcQuotaEnforcementAdapter(jdbc);
    service =
        new QuotaEnforcementService(
            adapter, new AuditService(new JdbcAuditOutboxAdapter(jdbc, new ObjectMapper())));
    transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    bind(TENANT_ONE);
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void enforcesAllDimensionsAtExactBoundaryAndKeepsDeniedHistory() {
    UUID policyId =
        insertPolicy(
            "all-dimensions",
            new QuotaLimits(10L, new BigDecimal("1.000000"), 1L, 1),
            QuotaWindowType.DAILY);
    Instant expiresAt = Instant.now().plusSeconds(3600);
    var request =
        request(policyId, execution(1), "quota-all-00000001", 10, "1.000000", 1, expiresAt);

    var allowed = inTransaction(() -> service.authorize(request));
    var duplicate = inTransaction(() -> service.authorize(request));
    var denied =
        inTransaction(
            () ->
                service.authorize(
                    request(
                        policyId,
                        execution(2),
                        "quota-all-00000002",
                        1,
                        "0.000001",
                        0,
                        expiresAt)));

    assertThat(allowed.allowed()).isTrue();
    assertThat(duplicate.duplicate()).isTrue();
    assertThat(denied.allowed()).isFalse();
    assertThat(denied.errorCode()).isEqualTo("GOV-B-001");
    assertThat(countReservations()).isEqualTo(2);
  }

  @Test
  void detectsConflictingIdempotencyAndReleasesOnlyLeasedDimensions() {
    UUID policyId =
        insertPolicy(
            "release-policy",
            new QuotaLimits(10L, new BigDecimal("1.000000"), 2L, 1),
            QuotaWindowType.DAILY);
    Instant expiresAt = Instant.now().plusSeconds(3600);
    var firstRequest =
        request(policyId, execution(3), "quota-release-0001", 10, "1.000000", 1, expiresAt);
    var first = inTransaction(() -> service.authorize(firstRequest));

    assertThatThrownBy(
            () ->
                inTransaction(
                    () ->
                        service.authorize(
                            request(
                                policyId,
                                execution(3),
                                "quota-release-0001",
                                9,
                                "1.000000",
                                1,
                                expiresAt))))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-I-001"));

    var blocked =
        inTransaction(
            () ->
                service.authorize(
                    request(policyId, execution(4), "quota-release-0002", 1, "0", 1, expiresAt)));
    assertThat(blocked.allowed()).isFalse();

    inTransaction(() -> service.release(first.reservation().id()));
    var afterRelease =
        inTransaction(
            () ->
                service.authorize(
                    request(
                        policyId,
                        execution(5),
                        "quota-release-0003",
                        10,
                        "1.000000",
                        1,
                        expiresAt)));
    var requestLimitReached =
        inTransaction(
            () ->
                service.authorize(
                    request(policyId, execution(6), "quota-release-0004", 0, "0", 0, expiresAt)));

    assertThat(afterRelease.allowed()).isTrue();
    assertThat(requestLimitReached.allowed()).isFalse();
    assertThat(
            adapter.release(UUID.fromString(TENANT_TWO), first.reservation().id(), Instant.now()))
        .isFalse();
    assertThat(adapter.reservation(UUID.fromString(TENANT_TWO), first.reservation().id()))
        .isEmpty();
  }

  @Test
  void expiresLeasesButKeepsAllowedRequestsCounted() {
    UUID policyId =
        insertPolicy("expiry-policy", new QuotaLimits(null, null, 10L, 1), QuotaWindowType.DAILY);
    Instant expiresAt = Instant.now().plusSeconds(60);
    var admitted =
        inTransaction(
            () ->
                service.authorize(
                    request(policyId, execution(7), "quota-expiry-00001", 0, "0", 1, expiresAt)));

    var consumption =
        adapter.consumption(
            UUID.fromString(TENANT_ONE),
            policyId,
            execution(7),
            admitted.reservation().window(),
            expiresAt.plusNanos(1));

    assertThat(consumption.concurrencyUnits()).isZero();
    assertThat(consumption.requestUnits()).isEqualTo(1);
  }

  @Test
  void scopesExecutionWindowsAndPersistedUsageCorrectly() {
    UUID executionPolicy =
        insertPolicy(
            "execution-policy", new QuotaLimits(null, null, 1L, null), QuotaWindowType.EXECUTION);
    Instant expiresAt = Instant.now().plusSeconds(3600);

    assertThat(
            inTransaction(
                    () ->
                        service.authorize(
                            request(
                                executionPolicy,
                                execution(8),
                                "quota-execution-01",
                                0,
                                "0",
                                0,
                                expiresAt)))
                .allowed())
        .isTrue();
    assertThat(
            inTransaction(
                    () ->
                        service.authorize(
                            request(
                                executionPolicy,
                                execution(9),
                                "quota-execution-02",
                                0,
                                "0",
                                0,
                                expiresAt)))
                .allowed())
        .isTrue();

    insertUsage(execution(8), 4, 3, "0.500000");
    Instant now = Instant.now();
    Instant start =
        now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    var usage =
        adapter.consumption(
            UUID.fromString(TENANT_ONE),
            executionPolicy,
            execution(8),
            new QuotaWindow(QuotaWindowType.DAILY, start, start.plusSeconds(86400)),
            now);

    assertThat(usage.tokenUnits()).isEqualTo(7);
    assertThat(usage.costAmount()).isEqualByComparingTo("0.500000");
  }

  @Test
  void serializesCompetingAdmissionsWithoutOverselling() throws Exception {
    UUID policyId =
        insertPolicy(
            "concurrent-policy", new QuotaLimits(null, null, 1L, 1), QuotaWindowType.DAILY);
    int attempts = 8;
    Instant expiresAt = Instant.now().plusSeconds(3600);
    CountDownLatch ready = new CountDownLatch(attempts);
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(attempts);
    List<Future<Boolean>> futures = new ArrayList<>();
    try {
      for (int index = 0; index < attempts; index++) {
        int value = index;
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent quota test did not start in time");
                  }
                  bind(TENANT_ONE);
                  try {
                    return inTransaction(
                            () ->
                                service.authorize(
                                    request(
                                        policyId,
                                        execution(20 + value),
                                        String.format("quota-concurrent-%04d", value),
                                        0,
                                        "0",
                                        1,
                                        expiresAt)))
                        .allowed();
                  } finally {
                    TenantContextHolder.clear();
                  }
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      int allowed = 0;
      for (Future<Boolean> future : futures) {
        if (future.get(30, TimeUnit.SECONDS)) {
          allowed++;
        }
      }
      assertThat(allowed).isEqualTo(1);
      assertThat(countReservations()).isEqualTo(attempts);
    } finally {
      executor.shutdownNow();
    }
  }

  private <T> T inTransaction(java.util.concurrent.Callable<T> action) {
    return transactions.execute(
        status -> {
          try {
            return action.call();
          } catch (RuntimeException exception) {
            throw exception;
          } catch (Exception exception) {
            throw new IllegalStateException(exception);
          }
        });
  }

  private QuotaAdmissionRequest request(
      UUID policyId,
      UUID executionId,
      String key,
      long tokens,
      String cost,
      int concurrency,
      Instant expiresAt) {
    return new QuotaAdmissionRequest(
        UUID.fromString(TENANT_ONE),
        policyId,
        executionId,
        key,
        tokens,
        new BigDecimal(cost),
        concurrency,
        expiresAt);
  }

  private UUID insertPolicy(String name, QuotaLimits limits, QuotaWindowType windowType) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO governance_quota_policies
          (id, tenant_id, name, policy_version, token_limit, cost_limit, request_limit,
           concurrency_limit, window_type, revision, created_at, updated_at)
        VALUES (?, ?, ?, 'policy-1', ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id.toString(),
        TENANT_ONE,
        name,
        limits.tokenLimit(),
        limits.costLimit(),
        limits.requestLimit(),
        limits.concurrencyLimit(),
        windowType.name());
    return id;
  }

  private void insertUsage(UUID executionId, long input, long output, String cost) {
    String providerId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    String modelId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    String pricingId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    jdbc.update(
        """
        INSERT INTO governance_providers
          (id, tenant_id, name, endpoint_policy_json, secret_ref, capabilities_json, state,
           created_at, updated_at)
        VALUES (?, ?, 'provider', '{}', 'secret://provider', '[]', 'ENABLED',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        providerId,
        TENANT_ONE);
    jdbc.update(
        """
        INSERT INTO governance_models
          (id, tenant_id, provider_id, name, state, policy_version, created_at, updated_at)
        VALUES (?, ?, ?, 'model', 'ENABLED', 'policy-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        modelId,
        TENANT_ONE,
        providerId);
    jdbc.update(
        """
        INSERT INTO governance_pricing_snapshots
          (id, tenant_id, provider_id, model_id, version, input_unit_price, output_unit_price,
           currency, rounding_mode, created_at)
        VALUES (?, ?, ?, ?, 1, 0.1, 0.1, 'USD', 'HALF_EVEN', CURRENT_TIMESTAMP)
        """,
        pricingId,
        TENANT_ONE,
        providerId,
        modelId);
    jdbc.update(
        """
        INSERT INTO governance_usage_records
          (id, tenant_id, execution_id, provider_request_id, usage_revision, pricing_snapshot_id,
           unit_type, input_units, output_units, currency, rounding_mode, calculated_amount,
           request_id, trace_id, source_ref, created_at)
        VALUES (?, ?, ?, 'provider-request', 1, ?, 'TOKEN', ?, ?, 'USD', 'HALF_EVEN', ?,
                'request-usage', '0123456789abcdef0123456789abcdef', 'agent:test', CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID().toString(),
        TENANT_ONE,
        executionId.toString(),
        pricingId,
        input,
        output,
        new BigDecimal(cost));
  }

  private int countReservations() {
    Integer count =
        jdbc.queryForObject("SELECT COUNT(*) FROM governance_quota_reservations", Integer.class);
    if (count == null) {
      throw new IllegalStateException("Quota reservation count was not returned");
    }
    return count;
  }

  private static UUID execution(int value) {
    return UUID.fromString(String.format("44444444-4444-4444-8444-%012d", value));
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
            "request-quota-enforcement",
            "0123456789abcdef0123456789abcdef",
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
