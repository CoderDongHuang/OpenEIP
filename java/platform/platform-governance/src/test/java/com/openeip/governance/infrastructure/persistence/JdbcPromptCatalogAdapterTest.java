package com.openeip.governance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.catalog.PromptCatalogService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.catalog.Prompt;
import com.openeip.governance.domain.catalog.PromptRegistration;
import com.openeip.governance.domain.catalog.PromptState;
import com.openeip.governance.domain.catalog.PromptVersion;
import com.openeip.governance.domain.catalog.PromptVersionRegistration;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.infrastructure.policy.AesGcmPromptContentCipher;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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

class JdbcPromptCatalogAdapterTest {
  private static final String TENANT_ONE = "11111111-1111-4111-8111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-4222-8222-222222222222";
  private static final UUID PRINCIPAL = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
  private JdbcTemplate jdbc;
  private JdbcPromptCatalogAdapter catalog;
  private PromptCatalogService service;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:governance-prompt-"
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
    catalog = new JdbcPromptCatalogAdapter(jdbc, new ObjectMapper());
    service =
        new PromptCatalogService(
            catalog,
            new AuditService(new JdbcAuditOutboxAdapter(jdbc, new ObjectMapper())),
            new AesGcmPromptContentCipher(Base64.getEncoder().encodeToString(new byte[32])));
    bind(TENANT_ONE);
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void encryptsPromptContentAndStoresOnlyVersionMetadataInAudit() {
    String content = "You are an internal operations assistant.";
    Prompt prompt = service.createPrompt(promptRegistration(content, "operations"));
    PromptVersion version =
        catalog.latestVersion(UUID.fromString(TENANT_ONE), prompt.id()).orElseThrow();

    assertThat(prompt.state()).isEqualTo(PromptState.DRAFT);
    assertThat(version.contentCiphertext()).startsWith("v1:").doesNotContain(content);
    assertThat(version.contentDigest())
        .isEqualTo(
            new AesGcmPromptContentCipher(Base64.getEncoder().encodeToString(new byte[32]))
                .digest(content));
    assertThat(
            jdbc.queryForObject(
                "SELECT content_ciphertext FROM governance_prompt_versions", String.class))
        .doesNotContain(content);
    String auditSummary =
        jdbc.queryForObject("SELECT summary_json FROM governance_audit_records", String.class);
    assertThat(auditSummary).contains(prompt.id().toString()).doesNotContain(content);
  }

  @Test
  void createsImmutableVersionsAndRequiresReviewAndEvaluationBeforePublish() {
    Prompt prompt = service.createPrompt(promptRegistration("version one", "operations"));
    UUID promptId = prompt.id();
    PromptVersion first =
        catalog.latestVersion(UUID.fromString(TENANT_ONE), prompt.id()).orElseThrow();
    PromptVersion second =
        service.createVersion(
            new PromptVersionRegistration(
                UUID.fromString(TENANT_ONE),
                prompt.id(),
                "version two",
                "prompt-v2",
                PRINCIPAL,
                NOW));

    assertThat(second.versionNumber()).isEqualTo(2);
    assertThat(first.contentDigest()).isNotEqualTo(second.contentDigest());
    assertThatThrownBy(
            () -> service.publish(promptId, second.id(), UUID.randomUUID().toString(), 0))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-002"));

    service.reviewVersion(prompt.id(), second.id(), "APPROVE", "Security review passed");
    var evaluation = service.evaluateVersion(prompt.id(), second.id(), UUID.randomUUID());
    assertThat(evaluation.evaluationRunId()).isNotNull();
    prompt = service.publish(prompt.id(), second.id(), evaluation.evaluationRunId().toString(), 0);

    assertThat(prompt.state()).isEqualTo(PromptState.PUBLISHED);
    assertThat(prompt.revision()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM governance_prompt_versions", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void rollbackCreatesNewReferenceWithoutChangingVersionHistory() {
    Prompt prompt = service.createPrompt(promptRegistration("version one", "operations"));
    PromptVersion first =
        catalog.latestVersion(UUID.fromString(TENANT_ONE), prompt.id()).orElseThrow();
    service.reviewVersion(prompt.id(), first.id(), "APPROVE", "Approved");
    var firstEvaluation = service.evaluateVersion(prompt.id(), first.id(), UUID.randomUUID());
    prompt =
        service.publish(prompt.id(), first.id(), firstEvaluation.evaluationRunId().toString(), 0);

    PromptVersion second =
        service.createVersion(
            new PromptVersionRegistration(
                UUID.fromString(TENANT_ONE),
                prompt.id(),
                "version two",
                "prompt-v2",
                PRINCIPAL,
                NOW));
    service.reviewVersion(prompt.id(), second.id(), "APPROVE", "Approved");
    var secondEvaluation = service.evaluateVersion(prompt.id(), second.id(), UUID.randomUUID());
    prompt =
        service.publish(prompt.id(), second.id(), secondEvaluation.evaluationRunId().toString(), 1);
    prompt = service.rollback(prompt.id(), first.id(), "Restore validated version");

    assertThat(prompt.state()).isEqualTo(PromptState.PUBLISHED);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM governance_prompt_publications", Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM governance_prompt_publications WHERE active = TRUE",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT prompt_version_id FROM governance_prompt_publications WHERE active = TRUE",
                String.class))
        .isEqualTo(first.id().toString());
  }

  @Test
  void rejectsDeniedReviewAndStalePublicationWithoutChangingState() {
    Prompt prompt = service.createPrompt(promptRegistration("version one", "operations"));
    PromptVersion version =
        catalog.latestVersion(UUID.fromString(TENANT_ONE), prompt.id()).orElseThrow();
    service.reviewVersion(prompt.id(), version.id(), "REJECT", "Missing evaluation evidence");

    assertThatThrownBy(() -> service.evaluateVersion(prompt.id(), version.id(), UUID.randomUUID()))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-002"));
    assertThatThrownBy(
            () -> service.publish(prompt.id(), version.id(), UUID.randomUUID().toString(), 9))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-001"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM governance_prompt_publications", Integer.class))
        .isZero();
  }

  @Test
  void appliesTenantIsolationToPromptQueriesAndCommands() {
    service.createPrompt(promptRegistration("tenant one prompt", "operations"));
    bind(TENANT_TWO);

    assertThat(service.listPrompts(null, 20)).isEmpty();
    assertThatThrownBy(
            () ->
                service.createVersion(
                    new PromptVersionRegistration(
                        UUID.fromString(TENANT_ONE),
                        UUID.randomUUID(),
                        "cross tenant",
                        "prompt-v1",
                        PRINCIPAL,
                        NOW)))
        .isInstanceOf(GovernanceCatalogException.class);
  }

  @Test
  void encryptionRequiresAConfigured256BitKeyAndUsesFreshNonce() {
    assertThatThrownBy(() -> new AesGcmPromptContentCipher("not-a-key"))
        .isInstanceOf(IllegalArgumentException.class);
    var cipher = new AesGcmPromptContentCipher(Base64.getEncoder().encodeToString(new byte[32]));
    assertThat(cipher.encrypt("same content")).isNotEqualTo(cipher.encrypt("same content"));
  }

  private PromptRegistration promptRegistration(String content, String purpose) {
    return new PromptRegistration(
        UUID.fromString(TENANT_ONE),
        "operations-assistant",
        purpose,
        content,
        "prompt-v1",
        PRINCIPAL,
        NOW);
  }

  private void bind(String tenantId) {
    TenantContextHolder.bind(
        new TenantContext(
            UUID.fromString(tenantId),
            null,
            PRINCIPAL,
            UUID.randomUUID(),
            Set.of("ROLE_GOVERNANCE_ADMIN"),
            "policy-1",
            "request-1",
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
