package com.openeip.governance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.catalog.ModelCatalogService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.application.usage.PricingSnapshotService;
import com.openeip.governance.application.usage.UsageLedgerService;
import com.openeip.governance.domain.catalog.ModelRegistration;
import com.openeip.governance.domain.catalog.ProviderRegistration;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.domain.usage.PricingSnapshotRegistration;
import com.openeip.governance.domain.usage.UsageRegistration;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
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

class JdbcPricingUsageAdapterTest {
  private static final String TENANT_ONE = "11111111-1111-4111-8111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-4222-8222-222222222222";
  private static final UUID PRINCIPAL = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final String REQUEST_ID = "request-usage-1";
  private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
  private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
  private JdbcTemplate jdbc;
  private JdbcPricingUsageAdapter adapter;
  private PricingSnapshotService pricingService;
  private UsageLedgerService usageService;
  private UUID pricingSnapshotId;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:governance-usage-"
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
    var auditAdapter = new JdbcAuditOutboxAdapter(jdbc, new ObjectMapper());
    var modelCatalog = new JdbcModelCatalogAdapter(jdbc, new ObjectMapper());
    var modelService = new ModelCatalogService(modelCatalog, new AuditService(auditAdapter));
    bind(TENANT_ONE);
    var provider = modelService.registerProvider(providerRegistration());
    var model = modelService.registerModel(modelRegistration(provider.id()));
    adapter = new JdbcPricingUsageAdapter(jdbc);
    pricingService =
        new PricingSnapshotService(adapter, modelCatalog, new AuditService(auditAdapter));
    usageService = new UsageLedgerService(adapter, adapter, new AuditService(auditAdapter));
    pricingSnapshotId = pricingService.create(pricingRegistration(provider.id(), model.id())).id();
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void calculatesCostFromImmutableSnapshotAndStoresHistoricalAmount() {
    var result = usageService.append(usageRegistration(10, 5, "source-a"));

    assertThat(result.duplicate()).isFalse();
    assertThat(result.record().calculatedAmount()).isEqualByComparingTo("0.250000");
    assertThat(result.record().currency()).isEqualTo("USD");
    assertThat(result.record().roundingMode()).isEqualTo("HALF_UP");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_usage_records", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void retriesAreIdempotentButDifferentFactsConflict() {
    var registration = usageRegistration(10, 5, "source-a");
    var first = usageService.append(registration);
    var retry = usageService.append(registration);

    assertThat(retry.duplicate()).isTrue();
    assertThat(retry.record().id()).isEqualTo(first.record().id());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_usage_records", Integer.class))
        .isEqualTo(1);
    assertThatThrownBy(() -> usageService.append(usageRegistration(11, 5, "source-a")))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-001"));
  }

  @Test
  void listsByExecutionAndTimeRangeWithinTheActiveTenant() {
    usageService.append(usageRegistration(1, 1, "source-a", 0, NOW.minusSeconds(2)));
    var second = usageService.append(usageRegistration(2, 2, "source-b", 1));

    assertThat(
            usageService.list(
                second.record().executionId(), NOW.minusSeconds(1), NOW.plusSeconds(1), 20))
        .extracting(record -> record.sourceRef())
        .containsExactly("source-b");
    bind(TENANT_TWO);
    assertThat(usageService.list(null, null, null, 20)).isEmpty();
  }

  @Test
  void rejectsInvalidPriceAndCrossModelProviderPricing() {
    assertThatThrownBy(
            () ->
                new PricingSnapshotRegistration(
                    UUID.fromString(TENANT_ONE),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "price-v2",
                    new BigDecimal("-0.1"),
                    BigDecimal.ZERO,
                    "USD",
                    "HALF_UP",
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
    bind(TENANT_TWO);
    assertThatThrownBy(
            () -> pricingService.create(pricingRegistration(UUID.randomUUID(), UUID.randomUUID())))
        .isInstanceOf(GovernanceCatalogException.class);
  }

  private PricingSnapshotRegistration pricingRegistration(UUID providerId, UUID modelId) {
    return new PricingSnapshotRegistration(
        UUID.fromString(TENANT_ONE),
        providerId,
        modelId,
        "price-v1",
        new BigDecimal("0.0100000000"),
        new BigDecimal("0.0300000000"),
        "USD",
        "HALF_UP",
        NOW);
  }

  private UsageRegistration usageRegistration(long input, long output, String source) {
    return usageRegistration(input, output, source, 0);
  }

  private UsageRegistration usageRegistration(
      long input, long output, String source, long revision) {
    return usageRegistration(input, output, source, revision, NOW);
  }

  private UsageRegistration usageRegistration(
      long input, long output, String source, long revision, Instant now) {
    return new UsageRegistration(
        UUID.fromString(TENANT_ONE),
        UUID.fromString("44444444-4444-4444-8444-444444444444"),
        "provider-request-1",
        revision,
        pricingSnapshotId,
        "TOKENS",
        input,
        output,
        REQUEST_ID,
        TRACE_ID,
        source,
        now);
  }

  private ProviderRegistration providerRegistration() {
    return new ProviderRegistration(
        UUID.fromString(TENANT_ONE),
        "provider-one",
        Map.of("baseUrl", "https://api.example"),
        "secret://env/PROVIDER_KEY",
        Set.of("chat"),
        NOW);
  }

  private ModelRegistration modelRegistration(UUID providerId) {
    return new ModelRegistration(
        UUID.fromString(TENANT_ONE),
        providerId,
        "model-one",
        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        Set.of("chat"),
        Set.of("fast"),
        null,
        NOW);
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
            Instant.parse("2026-09-03T23:59:59Z")));
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
