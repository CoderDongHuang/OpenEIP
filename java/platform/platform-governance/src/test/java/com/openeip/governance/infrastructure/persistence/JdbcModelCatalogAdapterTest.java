package com.openeip.governance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.catalog.ModelCatalogService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.catalog.ModelRegistration;
import com.openeip.governance.domain.catalog.ModelState;
import com.openeip.governance.domain.catalog.ProviderRegistration;
import com.openeip.governance.domain.catalog.ProviderState;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class JdbcModelCatalogAdapterTest {
  private static final String TENANT_ONE = "11111111-1111-4111-8111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-4222-8222-222222222222";
  private static final UUID PRINCIPAL = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
  private JdbcTemplate jdbc;
  private JdbcModelCatalogAdapter catalog;
  private ModelCatalogService service;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:governance-catalog-"
            + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(dataSource);
    String migration =
        new org.springframework.core.io.ClassPathResource(
                "db/migration/V2.7.0__init_governance_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    try (var connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection,
          new org.springframework.core.io.ByteArrayResource(
              h2Compatible(migration).getBytes(StandardCharsets.UTF_8)));
    }
    insertTenant(TENANT_ONE, "tenant-one");
    insertTenant(TENANT_TWO, "tenant-two");
    var auditAdapter = new JdbcAuditOutboxAdapter(jdbc, new ObjectMapper());
    catalog = new JdbcModelCatalogAdapter(jdbc, new ObjectMapper());
    service = new ModelCatalogService(catalog, new AuditService(auditAdapter));
    bind(TENANT_ONE);
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void storesProviderReferenceAndVersionedModelWithoutCredentialValue() {
    var provider = service.registerProvider(providerRegistration("provider-one"));
    var model = service.registerModel(modelRegistration(provider.id(), "model-one"));

    assertThat(provider.state()).isEqualTo(ProviderState.DRAFT);
    assertThat(provider.secretRef()).isEqualTo("secret://env/PROVIDER_KEY");
    assertThat(jdbc.queryForObject("SELECT secret_ref FROM governance_providers", String.class))
        .isEqualTo("secret://env/PROVIDER_KEY");
    assertThat(model.state()).isEqualTo(ModelState.DRAFT);
    assertThat(catalog.latestVersion(UUID.fromString(TENANT_ONE), model.id()))
        .get()
        .satisfies(
            version -> {
              assertThat(version.versionNumber()).isEqualTo(1);
              assertThat(version.contentDigest()).startsWith("sha256:");
            });
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_audit_records", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void lifecycleRequiresReviewAndEnabledProvider() {
    var provider = service.registerProvider(providerRegistration("provider-one"));
    var model = service.registerModel(modelRegistration(provider.id(), "model-one"));
    UUID modelId = model.id();

    assertThatThrownBy(() -> service.enableModel(modelId, 0))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-002"));
    model = service.reviewModel(modelId, 0);
    long reviewedRevision = model.revision();
    assertThatThrownBy(() -> service.enableModel(modelId, reviewedRevision))
        .isInstanceOf(GovernanceCatalogException.class)
        .hasMessageContaining("provider");
    service.enableProvider(provider.id(), 0);
    model = service.enableModel(modelId, reviewedRevision);
    assertThat(model.state()).isEqualTo(ModelState.ENABLED);
    assertThat(model.revision()).isEqualTo(2);
    model = service.suspendModel(modelId, model.revision());
    assertThat(model.state()).isEqualTo(ModelState.SUSPENDED);
  }

  @Test
  void staleRevisionAndInvalidTransitionsDoNotChangeState() {
    var provider = service.registerProvider(providerRegistration("provider-one"));
    var model = service.registerModel(modelRegistration(provider.id(), "model-one"));
    service.reviewModel(model.id(), 0);

    assertThatThrownBy(() -> service.reviewModel(model.id(), 0))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-002"));
    assertThatThrownBy(() -> service.enableProvider(provider.id(), 4))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-001"));
    assertThat(catalog.model(UUID.fromString(TENANT_ONE), model.id()).orElseThrow().revision())
        .isEqualTo(1);
  }

  @Test
  void modelAndProviderQueriesCannotCrossTenantBoundary() {
    var provider = service.registerProvider(providerRegistration("provider-one"));
    service.registerModel(modelRegistration(provider.id(), "model-one"));
    bind(TENANT_TWO);

    assertThat(service.listModels(null, null, 20)).isEmpty();
    assertThatThrownBy(() -> service.registerModel(modelRegistration(provider.id(), "model-two")))
        .isInstanceOf(GovernanceCatalogException.class);
  }

  @Test
  void duplicateModelNameAndVersionDoNotCreatePartialRows() {
    var provider = service.registerProvider(providerRegistration("provider-one"));
    var registration = modelRegistration(provider.id(), "model-one");
    service.registerModel(registration);
    assertThatThrownBy(() -> service.registerModel(registration))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-001"));
    UUID modelId = catalog.models(UUID.fromString(TENANT_ONE), null, null, 20).getFirst().id();
    assertThatThrownBy(() -> catalog.addVersion(registration, modelId, 1))
        .isInstanceOf(GovernanceCatalogException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_models", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_model_versions", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void rejectsCredentialValuesAndUntrustedModelRegistration() {
    assertThatThrownBy(
            () ->
                new ProviderRegistration(
                    UUID.fromString(TENANT_ONE),
                    "provider-one",
                    Map.of("baseUrl", "https://api.example"),
                    "sk-live-secret-value",
                    Set.of("chat"),
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ProviderRegistration(
                    UUID.fromString(TENANT_ONE),
                    "provider-one",
                    Map.of("authorization", "Bearer secret"),
                    "secret://env/PROVIDER_KEY",
                    Set.of("chat"),
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ModelRegistration(
                    UUID.fromString(TENANT_ONE),
                    UUID.randomUUID(),
                    "model-one",
                    "not-a-digest",
                    Set.of("chat"),
                    Set.of(),
                    null,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ProviderRegistration providerRegistration(String name) {
    return new ProviderRegistration(
        UUID.fromString(TENANT_ONE),
        name,
        Map.of("baseUrl", "https://api.example", "timeoutMs", 30000),
        "secret://env/PROVIDER_KEY",
        Set.of("chat", "streaming"),
        NOW);
  }

  private ModelRegistration modelRegistration(UUID providerId, String name) {
    return new ModelRegistration(
        UUID.fromString(TENANT_ONE),
        providerId,
        name,
        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        Set.of("chat", "streaming"),
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
            "request-1",
            "0123456789abcdef0123456789abcdef",
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
