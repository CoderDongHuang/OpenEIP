package com.openeip.governance.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.application.quota.QuotaEnforcementService;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.domain.quota.QuotaAdmissionRequest;
import com.openeip.governance.infrastructure.persistence.JdbcAuditOutboxAdapter;
import com.openeip.governance.infrastructure.persistence.JdbcQuotaEnforcementAdapter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("benchmark")
class GovernanceQuotaBenchmarkTest {
  private static final int WARMUPS = 20;
  private static final int SEQUENTIAL_ADMISSIONS = 1000;
  private static final int COMPETING_ADMISSIONS = 100;
  private static final int CONCURRENCY_LIMIT = 20;
  private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID PRINCIPAL = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Test
  void measuresSequentialAndCompetingAdmissions() throws Exception {
    JdbcDataSource dataSource = dataSource();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    migrate(dataSource);
    insertTenant(jdbc);
    JdbcQuotaEnforcementAdapter adapter = new JdbcQuotaEnforcementAdapter(jdbc);
    QuotaEnforcementService service =
        new QuotaEnforcementService(
            adapter, new AuditService(new JdbcAuditOutboxAdapter(jdbc, new ObjectMapper())));
    TransactionTemplate transactions =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    Instant expiresAt = Instant.now().plusSeconds(7200);

    UUID sequentialPolicy = insertPolicy(jdbc, "benchmark-sequential", 2000, null);
    bind();
    for (int index = 0; index < WARMUPS; index++) {
      int value = index;
      inTransaction(
          transactions,
          () ->
              service.authorize(
                  request(
                      sequentialPolicy,
                      execution(10_000 + value),
                      String.format("quota-warmup-%05d", value),
                      0,
                      expiresAt)));
    }

    double[] sequentialMilliseconds = new double[SEQUENTIAL_ADMISSIONS];
    int sequentialErrors = 0;
    for (int index = 0; index < SEQUENTIAL_ADMISSIONS; index++) {
      int value = index;
      long started = System.nanoTime();
      var result =
          inTransaction(
              transactions,
              () ->
                  service.authorize(
                      request(
                          sequentialPolicy,
                          execution(20_000 + value),
                          String.format("quota-sequential-%05d", value),
                          0,
                          expiresAt)));
      sequentialMilliseconds[index] = (System.nanoTime() - started) / 1_000_000.0;
      if (!result.allowed()) {
        sequentialErrors++;
      }
    }
    Arrays.sort(sequentialMilliseconds);

    UUID competingPolicy =
        insertPolicy(jdbc, "benchmark-competing", COMPETING_ADMISSIONS, CONCURRENCY_LIMIT);
    List<Callable<Boolean>> work = new ArrayList<>();
    for (int index = 0; index < COMPETING_ADMISSIONS; index++) {
      int value = index;
      work.add(
          () -> {
            bind();
            try {
              return inTransaction(
                      transactions,
                      () ->
                          service.authorize(
                              request(
                                  competingPolicy,
                                  execution(30_000 + value),
                                  String.format("quota-competing-%05d", value),
                                  1,
                                  expiresAt)))
                  .allowed();
            } finally {
              TenantContextHolder.clear();
            }
          });
    }
    var pool = Executors.newFixedThreadPool(16);
    long competingStarted = System.nanoTime();
    var futures = pool.invokeAll(work);
    long competingNanos = System.nanoTime() - competingStarted;
    pool.shutdown();
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    int competingAllowed = 0;
    int competingErrors = 0;
    for (var future : futures) {
      try {
        if (future.get()) {
          competingAllowed++;
        }
      } catch (Exception exception) {
        competingErrors++;
      }
    }
    TenantContextHolder.clear();

    double p50 = percentile(sequentialMilliseconds, 0.50);
    double p95 = percentile(sequentialMilliseconds, 0.95);
    double p99 = percentile(sequentialMilliseconds, 0.99);
    double competingWallMs = competingNanos / 1_000_000.0;
    Integer reservationCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM governance_quota_reservations", Integer.class);
    if (reservationCount == null) {
      throw new IllegalStateException("Quota reservation count was not returned");
    }
    int reservationRows = reservationCount;

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("schemaVersion", 1);
    result.put("recordedAt", Instant.now().toString());
    result.put("runtime", System.getProperty("java.runtime.version"));
    result.put("platform", System.getProperty("os.name") + " " + System.getProperty("os.version"));
    result.put("database", "H2 MySQL mode");
    result.put("warmups", WARMUPS);
    result.put("sequentialAdmissions", SEQUENTIAL_ADMISSIONS);
    result.put("sequentialP50Ms", round(p50));
    result.put("sequentialP95Ms", round(p95));
    result.put("sequentialP99Ms", round(p99));
    result.put("thresholdP99Ms", 50);
    result.put("competingAdmissions", COMPETING_ADMISSIONS);
    result.put("competingAllowed", competingAllowed);
    result.put("competingExpectedAllowed", CONCURRENCY_LIMIT);
    result.put("competingWallTimeMs", round(competingWallMs));
    result.put(
        "competingThroughputPerSecond", round(COMPETING_ADMISSIONS * 1000.0 / competingWallMs));
    result.put("reservationRows", reservationRows);
    result.put("expectedReservationRows", WARMUPS + SEQUENTIAL_ADMISSIONS + COMPETING_ADMISSIONS);
    result.put("errors", sequentialErrors + competingErrors);
    result.put(
        "result",
        p99 < 50
                && sequentialErrors == 0
                && competingErrors == 0
                && competingAllowed == CONCURRENCY_LIMIT
                && reservationRows == WARMUPS + SEQUENTIAL_ADMISSIONS + COMPETING_ADMISSIONS
            ? "PASS"
            : "FAIL");
    write(result);

    assertThat(p99).isLessThan(50.0);
    assertThat(sequentialErrors).isZero();
    assertThat(competingErrors).isZero();
    assertThat(competingAllowed).isEqualTo(CONCURRENCY_LIMIT);
    assertThat(reservationRows).isEqualTo(WARMUPS + SEQUENTIAL_ADMISSIONS + COMPETING_ADMISSIONS);
  }

  private static JdbcDataSource dataSource() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:governance-quota-benchmark;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
            + "DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000");
    return dataSource;
  }

  private static void migrate(JdbcDataSource dataSource) throws Exception {
    String migration =
        new ClassPathResource("db/migration/V2.7.0__init_governance_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    try (var connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection,
          new ByteArrayResource(h2Compatible(migration).getBytes(StandardCharsets.UTF_8)));
    }
  }

  private static void insertTenant(JdbcTemplate jdbc) {
    jdbc.update(
        """
        INSERT INTO governance_tenants
          (id, tenant_id, display_name, slug, state, policy_version, created_at, updated_at)
        VALUES (?, ?, 'Benchmark', 'benchmark', 'ACTIVE', 'policy-1',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        TENANT.toString(),
        TENANT.toString());
  }

  private static UUID insertPolicy(
      JdbcTemplate jdbc, String name, long requestLimit, Integer concurrencyLimit) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO governance_quota_policies
          (id, tenant_id, name, policy_version, request_limit, concurrency_limit,
           window_type, revision, created_at, updated_at)
        VALUES (?, ?, ?, 'policy-1', ?, ?, 'DAILY', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id.toString(),
        TENANT.toString(),
        name,
        requestLimit,
        concurrencyLimit);
    return id;
  }

  private static QuotaAdmissionRequest request(
      UUID policyId, UUID executionId, String key, int concurrency, Instant expiresAt) {
    return new QuotaAdmissionRequest(
        TENANT, policyId, executionId, key, 0, BigDecimal.ZERO, concurrency, expiresAt);
  }

  private static <T> T inTransaction(TransactionTemplate transactions, Callable<T> action) {
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

  private static void bind() {
    TenantContextHolder.bind(
        new TenantContext(
            TENANT,
            null,
            PRINCIPAL,
            UUID.randomUUID(),
            Set.of("ROLE_OPERATOR"),
            "policy-1",
            "request-governance-benchmark",
            "0123456789abcdef0123456789abcdef",
            GovernanceScope.TENANT,
            Instant.parse("2099-01-01T00:00:00Z")));
  }

  private static UUID execution(int value) {
    return UUID.fromString(String.format("44444444-4444-4444-8444-%012d", value));
  }

  private static double percentile(double[] sorted, double percentile) {
    int index = (int) Math.ceil(percentile * sorted.length) - 1;
    return sorted[Math.max(index, 0)];
  }

  private static double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }

  private static void write(Map<String, Object> report) throws Exception {
    String property =
        Objects.requireNonNull(
            System.getProperty("governanceQuotaBenchmarkOutput"),
            "governanceQuotaBenchmarkOutput is required");
    Path output = Path.of(property).toAbsolutePath();
    Files.createDirectories(Objects.requireNonNull(output.getParent()));
    new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .writeValue(output.toFile(), report);
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
