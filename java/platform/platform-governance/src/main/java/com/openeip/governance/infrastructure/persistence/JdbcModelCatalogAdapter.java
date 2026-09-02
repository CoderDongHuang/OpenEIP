package com.openeip.governance.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openeip.governance.application.catalog.ModelCatalogPort;
import com.openeip.governance.domain.catalog.Model;
import com.openeip.governance.domain.catalog.ModelRegistration;
import com.openeip.governance.domain.catalog.ModelState;
import com.openeip.governance.domain.catalog.ModelVersion;
import com.openeip.governance.domain.catalog.Provider;
import com.openeip.governance.domain.catalog.ProviderRegistration;
import com.openeip.governance.domain.catalog.ProviderState;
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

/** JDBC adapter for tenant-scoped provider policies and immutable model versions. */
@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JdbcTemplate and ObjectMapper are application-scoped collaborators.")
public class JdbcModelCatalogAdapter implements ModelCatalogPort {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcModelCatalogAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public Provider registerProvider(ProviderRegistration registration) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO governance_providers
            (id, tenant_id, name, endpoint_policy_json, secret_ref, capabilities_json,
             state, revision, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', 0, ?, ?)
          """,
          id.toString(),
          registration.tenantId().toString(),
          registration.name(),
          json(registration.endpointPolicy()),
          registration.secretRef(),
          json(registration.capabilities()),
          timestamp(registration.now()),
          timestamp(registration.now()));
      return provider(registration.tenantId(), id).orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw GovernanceCatalogException.conflict("Provider name already exists in this tenant");
    } catch (JsonProcessingException exception) {
      throw GovernanceCatalogException.invalid("Provider policy cannot be serialized");
    }
  }

  @Override
  public Optional<Provider> provider(UUID tenantId, UUID providerId) {
    return jdbc
        .query(
            "SELECT * FROM governance_providers WHERE tenant_id = ? AND id = ?",
            (rs, row) -> provider(rs),
            tenantId.toString(),
            providerId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Provider> providerByName(UUID tenantId, String name) {
    return jdbc
        .query(
            "SELECT * FROM governance_providers WHERE tenant_id = ? AND name = ?",
            (rs, row) -> provider(rs),
            tenantId.toString(),
            name)
        .stream()
        .findFirst();
  }

  @Override
  public boolean updateProviderState(
      UUID tenantId, UUID providerId, long expectedRevision, ProviderState state) {
    return jdbc.update(
            """
            UPDATE governance_providers
            SET state = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP(6)
            WHERE tenant_id = ? AND id = ? AND revision = ?
            """,
            state.name(),
            tenantId.toString(),
            providerId.toString(),
            expectedRevision)
        == 1;
  }

  @Override
  @Transactional
  public Model registerModel(ModelRegistration registration, String policyVersion) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO governance_models
            (id, tenant_id, provider_id, name, state, policy_version, revision, created_at, updated_at)
          VALUES (?, ?, ?, ?, 'DRAFT', ?, 0, ?, ?)
          """,
          id.toString(),
          registration.tenantId().toString(),
          registration.providerId().toString(),
          registration.name(),
          policyVersion,
          timestamp(registration.now()),
          timestamp(registration.now()));
      return model(registration.tenantId(), id).orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw GovernanceCatalogException.conflict("Model name already exists in this tenant");
    }
  }

  @Override
  public Optional<Model> model(UUID tenantId, UUID modelId) {
    return jdbc
        .query(
            "SELECT * FROM governance_models WHERE tenant_id = ? AND id = ?",
            (rs, row) -> model(rs),
            tenantId.toString(),
            modelId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public List<Model> models(UUID tenantId, String state, String capability, int limit) {
    String normalizedState = state == null || state.isBlank() ? null : state.toUpperCase();
    String capabilityPattern =
        capability == null || capability.isBlank() ? null : "%\"" + capability + "\"%";
    return jdbc.query(
        """
            SELECT DISTINCT m.*
            FROM governance_models m
            LEFT JOIN governance_model_versions v
              ON v.tenant_id = m.tenant_id AND v.model_id = m.id
            WHERE m.tenant_id = ?
              AND (? IS NULL OR m.state = ?)
              AND (? IS NULL OR v.capabilities_json LIKE ?)
            ORDER BY m.updated_at DESC, m.id DESC
            LIMIT ?
            """,
        (rs, row) -> model(rs),
        tenantId.toString(),
        normalizedState,
        normalizedState,
        capabilityPattern,
        capabilityPattern,
        limit);
  }

  @Override
  public Optional<ModelVersion> latestVersion(UUID tenantId, UUID modelId) {
    return jdbc
        .query(
            """
            SELECT * FROM governance_model_versions
            WHERE tenant_id = ? AND model_id = ?
            ORDER BY version_number DESC
            LIMIT 1
            """,
            (rs, row) -> version(rs),
            tenantId.toString(),
            modelId.toString())
        .stream()
        .findFirst();
  }

  @Override
  @Transactional
  public ModelVersion addVersion(ModelRegistration registration, UUID modelId, int versionNumber) {
    if (versionNumber < 1) {
      throw GovernanceCatalogException.invalid("Model version must be positive");
    }
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO governance_model_versions
            (id, tenant_id, model_id, version_number, content_digest, capabilities_json,
             routing_labels_json, pricing_snapshot_id, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          id.toString(),
          registration.tenantId().toString(),
          modelId.toString(),
          versionNumber,
          registration.contentDigest(),
          json(registration.capabilities()),
          json(registration.routingLabels()),
          registration.pricingSnapshotId() == null
              ? null
              : registration.pricingSnapshotId().toString(),
          timestamp(registration.now()));
      return version(registration.tenantId(), id).orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw GovernanceCatalogException.conflict("Model version already exists");
    } catch (JsonProcessingException exception) {
      throw GovernanceCatalogException.invalid("Model capabilities cannot be serialized");
    }
  }

  @Override
  public boolean updateModelState(
      UUID tenantId, UUID modelId, long expectedRevision, String state) {
    return jdbc.update(
            """
            UPDATE governance_models
            SET state = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP(6)
            WHERE tenant_id = ? AND id = ? AND revision = ?
            """,
            ModelState.valueOf(state).name(),
            tenantId.toString(),
            modelId.toString(),
            expectedRevision)
        == 1;
  }

  private Provider provider(java.sql.ResultSet rs) throws java.sql.SQLException {
    try {
      return new Provider(
          UUID.fromString(rs.getString("id")),
          UUID.fromString(rs.getString("tenant_id")),
          rs.getString("name"),
          mapper.readValue(rs.getString("endpoint_policy_json"), Map.class),
          rs.getString("secret_ref"),
          mapper.readValue(rs.getString("capabilities_json"), Set.class),
          ProviderState.valueOf(rs.getString("state")),
          rs.getLong("revision"),
          rs.getTimestamp("created_at").toInstant(),
          rs.getTimestamp("updated_at").toInstant());
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw GovernanceCatalogException.invalid("Stored provider policy is invalid");
    }
  }

  private Model model(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new Model(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        UUID.fromString(rs.getString("provider_id")),
        rs.getString("name"),
        ModelState.valueOf(rs.getString("state")),
        rs.getString("policy_version"),
        rs.getLong("revision"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private Optional<ModelVersion> version(UUID tenantId, UUID versionId) {
    return jdbc
        .query(
            "SELECT * FROM governance_model_versions WHERE tenant_id = ? AND id = ?",
            (rs, row) -> version(rs),
            tenantId.toString(),
            versionId.toString())
        .stream()
        .findFirst();
  }

  private ModelVersion version(java.sql.ResultSet rs) throws java.sql.SQLException {
    try {
      return new ModelVersion(
          UUID.fromString(rs.getString("id")),
          UUID.fromString(rs.getString("tenant_id")),
          UUID.fromString(rs.getString("model_id")),
          rs.getInt("version_number"),
          rs.getString("content_digest"),
          mapper.readValue(rs.getString("capabilities_json"), Set.class),
          mapper.readValue(rs.getString("routing_labels_json"), Set.class),
          nullableUuid(rs.getString("pricing_snapshot_id")),
          rs.getTimestamp("created_at").toInstant());
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw GovernanceCatalogException.invalid("Stored model version is invalid");
    }
  }

  private String json(Object value) throws JsonProcessingException {
    return mapper
        .writer()
        .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .writeValueAsString(value);
  }

  private static UUID nullableUuid(String value) {
    return value == null ? null : UUID.fromString(value);
  }

  private static Timestamp timestamp(Instant value) {
    return Timestamp.from(value);
  }
}
