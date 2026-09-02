package com.openeip.governance.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.governance.application.catalog.PromptCatalogPort;
import com.openeip.governance.domain.catalog.Prompt;
import com.openeip.governance.domain.catalog.PromptPublication;
import com.openeip.governance.domain.catalog.PromptRegistration;
import com.openeip.governance.domain.catalog.PromptReview;
import com.openeip.governance.domain.catalog.PromptState;
import com.openeip.governance.domain.catalog.PromptVersion;
import com.openeip.governance.domain.catalog.PromptVersionRegistration;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter for encrypted Prompt versions and append-only review/publication evidence. */
@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JdbcTemplate and ObjectMapper are application-scoped collaborators.")
public class JdbcPromptCatalogAdapter implements PromptCatalogPort {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final TypeReference<Set<String>> SET_TYPE = new TypeReference<>() {};
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcPromptCatalogAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public Prompt createPrompt(
      PromptRegistration registration, String contentCiphertext, String contentDigest) {
    UUID promptId = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO governance_prompts
            (id, tenant_id, name, purpose, active_publication_id, revision, created_at, updated_at)
          VALUES (?, ?, ?, ?, NULL, 0, ?, ?)
          """,
          promptId.toString(),
          registration.tenantId().toString(),
          registration.name(),
          registration.purpose(),
          timestamp(registration.now()),
          timestamp(registration.now()));
      insertVersion(
          registration.tenantId(),
          promptId,
          contentCiphertext,
          contentDigest,
          registration.compatibilityVersion(),
          registration.createdBy(),
          1,
          registration.now());
      return prompt(registration.tenantId(), promptId).orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw GovernanceCatalogException.conflict(
          "Prompt name and purpose already exist in this tenant");
    }
  }

  @Override
  public Optional<Prompt> prompt(UUID tenantId, UUID promptId) {
    return jdbc
        .query(
            "SELECT * FROM governance_prompts WHERE tenant_id = ? AND id = ?",
            (rs, row) -> prompt(rs),
            tenantId.toString(),
            promptId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public List<Prompt> prompts(UUID tenantId, String state, int limit) {
    String requested = state == null || state.isBlank() ? null : state.toUpperCase();
    return jdbc
        .query(
            "SELECT * FROM governance_prompts WHERE tenant_id = ? ORDER BY updated_at DESC, id DESC LIMIT ?",
            (rs, row) -> prompt(rs),
            tenantId.toString(),
            100)
        .stream()
        .filter(value -> requested == null || value.state().name().equals(requested))
        .limit(limit)
        .toList();
  }

  @Override
  @Transactional
  public PromptVersion createVersion(
      PromptVersionRegistration registration,
      String contentCiphertext,
      String contentDigest,
      int versionNumber) {
    try {
      insertVersion(
          registration.tenantId(),
          registration.promptId(),
          contentCiphertext,
          contentDigest,
          registration.compatibilityVersion(),
          registration.createdBy(),
          versionNumber,
          registration.now());
      return version(
              registration.tenantId(),
              registration.promptId(),
              findVersionId(registration.tenantId(), registration.promptId(), versionNumber))
          .orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw GovernanceCatalogException.conflict("Prompt version already exists");
    }
  }

  @Override
  public Optional<PromptVersion> version(UUID tenantId, UUID promptId, UUID versionId) {
    return jdbc
        .query(
            """
            SELECT * FROM governance_prompt_versions
            WHERE tenant_id = ? AND prompt_id = ? AND id = ?
            """,
            (rs, row) -> version(rs),
            tenantId.toString(),
            promptId.toString(),
            versionId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<PromptVersion> latestVersion(UUID tenantId, UUID promptId) {
    return jdbc
        .query(
            """
            SELECT * FROM governance_prompt_versions
            WHERE tenant_id = ? AND prompt_id = ?
            ORDER BY version_number DESC LIMIT 1
            """,
            (rs, row) -> version(rs),
            tenantId.toString(),
            promptId.toString())
        .stream()
        .findFirst();
  }

  @Override
  @Transactional
  public PromptReview addReview(
      UUID tenantId,
      UUID promptVersionId,
      UUID reviewerId,
      String decision,
      String reason,
      String policyVersion,
      UUID evaluationRunId,
      Instant now) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO governance_prompt_reviews
          (id, tenant_id, prompt_version_id, reviewer_id, decision, reason, policy_version,
           evaluation_run_id, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id.toString(),
        tenantId.toString(),
        promptVersionId.toString(),
        reviewerId.toString(),
        decision,
        reason,
        policyVersion,
        evaluationRunId == null ? null : evaluationRunId.toString(),
        timestamp(now));
    return review(tenantId, id).orElseThrow();
  }

  @Override
  public boolean hasApprovedReview(UUID tenantId, UUID promptVersionId) {
    return !jdbc.query(
            """
            SELECT id FROM governance_prompt_reviews
            WHERE tenant_id = ? AND prompt_version_id = ?
              AND decision = 'APPROVE' AND evaluation_run_id IS NULL
            LIMIT 1
            """,
            (rs, row) -> rs.getString(1),
            tenantId.toString(),
            promptVersionId.toString())
        .isEmpty();
  }

  @Override
  public boolean hasEvaluation(UUID tenantId, UUID promptVersionId, UUID evaluationRunId) {
    String sql =
        evaluationRunId == null
            ? """
              SELECT id FROM governance_prompt_reviews
              WHERE tenant_id = ? AND prompt_version_id = ?
                AND evaluation_run_id IS NOT NULL LIMIT 1
              """
            : """
              SELECT id FROM governance_prompt_reviews
              WHERE tenant_id = ? AND prompt_version_id = ? AND evaluation_run_id = ?
              LIMIT 1
              """;
    List<String> ids =
        evaluationRunId == null
            ? jdbc.query(
                sql, (rs, row) -> rs.getString(1), tenantId.toString(), promptVersionId.toString())
            : jdbc.query(
                sql,
                (rs, row) -> rs.getString(1),
                tenantId.toString(),
                promptVersionId.toString(),
                evaluationRunId.toString());
    return !ids.isEmpty();
  }

  @Override
  @Transactional
  public PromptPublication publish(
      UUID tenantId,
      UUID promptId,
      UUID promptVersionId,
      String contentDigest,
      String reason,
      String policyVersion,
      UUID createdBy,
      Instant now,
      long expectedRevision) {
    UUID publicationId = UUID.randomUUID();
    jdbc.query(
        "SELECT id FROM governance_prompts WHERE tenant_id = ? AND id = ? FOR UPDATE",
        (rs, row) -> rs.getString(1),
        tenantId.toString(),
        promptId.toString());
    jdbc.update(
        """
        UPDATE governance_prompt_publications
        SET active = FALSE
        WHERE tenant_id = ? AND prompt_id = ? AND active = TRUE
        """,
        tenantId.toString(),
        promptId.toString());
    jdbc.update(
        """
        INSERT INTO governance_prompt_publications
          (id, tenant_id, prompt_id, prompt_version_id, content_digest, publication_reason,
           policy_version, active, created_by, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
        """,
        publicationId.toString(),
        tenantId.toString(),
        promptId.toString(),
        promptVersionId.toString(),
        contentDigest,
        reason,
        policyVersion,
        createdBy.toString(),
        timestamp(now));
    if (jdbc.update(
            """
            UPDATE governance_prompts
            SET active_publication_id = ?, revision = revision + 1, updated_at = ?
            WHERE tenant_id = ? AND id = ? AND revision = ?
            """,
            publicationId.toString(),
            timestamp(now),
            tenantId.toString(),
            promptId.toString(),
            expectedRevision)
        != 1) {
      throw GovernanceCatalogException.conflict("Prompt revision is stale");
    }
    return publication(tenantId, publicationId).orElseThrow();
  }

  @Override
  public Optional<PromptPublication> publication(UUID tenantId, UUID publicationId) {
    return jdbc
        .query(
            "SELECT * FROM governance_prompt_publications WHERE tenant_id = ? AND id = ?",
            (rs, row) -> publication(rs),
            tenantId.toString(),
            publicationId.toString())
        .stream()
        .findFirst();
  }

  private void insertVersion(
      UUID tenantId,
      UUID promptId,
      String ciphertext,
      String digest,
      String compatibilityVersion,
      UUID createdBy,
      int versionNumber,
      Instant now) {
    jdbc.update(
        """
        INSERT INTO governance_prompt_versions
          (id, tenant_id, prompt_id, version_number, content_ciphertext, content_digest,
           compatibility_version, created_by, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID().toString(),
        tenantId.toString(),
        promptId.toString(),
        versionNumber,
        ciphertext,
        digest,
        compatibilityVersion,
        createdBy.toString(),
        timestamp(now));
  }

  private UUID findVersionId(UUID tenantId, UUID promptId, int versionNumber) {
    return jdbc.queryForObject(
        "SELECT id FROM governance_prompt_versions WHERE tenant_id = ? AND prompt_id = ? AND version_number = ?",
        (rs, row) -> UUID.fromString(rs.getString(1)),
        tenantId.toString(),
        promptId.toString(),
        versionNumber);
  }

  private Prompt prompt(java.sql.ResultSet rs) throws java.sql.SQLException {
    UUID tenantId = UUID.fromString(rs.getString("tenant_id"));
    UUID promptId = UUID.fromString(rs.getString("id"));
    UUID activePublicationId = nullableUuid(rs.getString("active_publication_id"));
    return new Prompt(
        promptId,
        tenantId,
        rs.getString("name"),
        rs.getString("purpose"),
        activePublicationId,
        rs.getLong("revision"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        state(tenantId, promptId, activePublicationId));
  }

  private PromptState state(UUID tenantId, UUID promptId, UUID activePublicationId) {
    if (activePublicationId != null) {
      return PromptState.PUBLISHED;
    }
    if (count(
            "SELECT COUNT(*) FROM governance_prompt_publications WHERE tenant_id = ? AND prompt_id = ?",
            tenantId,
            promptId)
        > 0) {
      return PromptState.DEPRECATED;
    }
    if (count(
            """
            SELECT COUNT(*)
            FROM governance_prompt_reviews r
            JOIN governance_prompt_versions v
              ON v.tenant_id = r.tenant_id AND v.id = r.prompt_version_id
            WHERE r.tenant_id = ? AND v.prompt_id = ? AND r.evaluation_run_id IS NOT NULL
            """,
            tenantId,
            promptId)
        > 0) {
      return PromptState.EVALUATED;
    }
    if (count(
            """
            SELECT COUNT(*)
            FROM governance_prompt_reviews r
            JOIN governance_prompt_versions v
              ON v.tenant_id = r.tenant_id AND v.id = r.prompt_version_id
            WHERE r.tenant_id = ? AND v.prompt_id = ?
            """,
            tenantId,
            promptId)
        > 0) {
      return PromptState.IN_REVIEW;
    }
    return PromptState.DRAFT;
  }

  private int count(String sql, UUID tenantId, UUID promptId) {
    Integer result =
        jdbc.queryForObject(sql, Integer.class, tenantId.toString(), promptId.toString());
    return result == null ? 0 : result;
  }

  private PromptVersion version(java.sql.ResultSet rs) throws java.sql.SQLException {
    try {
      return new PromptVersion(
          UUID.fromString(rs.getString("id")),
          UUID.fromString(rs.getString("tenant_id")),
          UUID.fromString(rs.getString("prompt_id")),
          rs.getInt("version_number"),
          rs.getString("content_ciphertext"),
          rs.getString("content_digest"),
          rs.getString("compatibility_version"),
          UUID.fromString(rs.getString("created_by")),
          rs.getTimestamp("created_at").toInstant());
    } catch (IllegalArgumentException exception) {
      throw GovernanceCatalogException.invalid("Stored Prompt version is invalid");
    }
  }

  private Optional<PromptReview> review(UUID tenantId, UUID reviewId) {
    return jdbc
        .query(
            "SELECT * FROM governance_prompt_reviews WHERE tenant_id = ? AND id = ?",
            (rs, row) -> review(rs),
            tenantId.toString(),
            reviewId.toString())
        .stream()
        .findFirst();
  }

  private PromptReview review(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new PromptReview(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        UUID.fromString(rs.getString("prompt_version_id")),
        UUID.fromString(rs.getString("reviewer_id")),
        rs.getString("decision"),
        rs.getString("reason"),
        rs.getString("policy_version"),
        nullableUuid(rs.getString("evaluation_run_id")),
        rs.getTimestamp("created_at").toInstant());
  }

  private PromptPublication publication(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new PromptPublication(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        UUID.fromString(rs.getString("prompt_id")),
        UUID.fromString(rs.getString("prompt_version_id")),
        rs.getString("content_digest"),
        rs.getString("publication_reason"),
        rs.getString("policy_version"),
        rs.getBoolean("active"),
        UUID.fromString(rs.getString("created_by")),
        rs.getTimestamp("created_at").toInstant());
  }

  private static UUID nullableUuid(String value) {
    return value == null ? null : UUID.fromString(value);
  }

  private static Timestamp timestamp(Instant value) {
    return Timestamp.from(value);
  }
}
