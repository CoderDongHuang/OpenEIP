package com.openeip.marketplace.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openeip.marketplace.application.MarketplacePort;
import com.openeip.marketplace.domain.MarketplacePackage;
import com.openeip.marketplace.domain.PackageState;
import com.openeip.marketplace.domain.PackageType;
import com.openeip.marketplace.domain.PackageVersion;
import com.openeip.marketplace.shared.MarketplaceException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JDBC collaborators are application scoped.")
public class JdbcMarketplaceAdapter implements MarketplacePort {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcMarketplaceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public MarketplacePackage createPackage(
      UUID tenantId,
      PackageType type,
      String slug,
      String displayName,
      String description,
      String publisher) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO marketplace_packages
             (id, tenant_id, package_type, slug, display_name, description, publisher, state,
              revision, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
          """,
          id.toString(),
          tenantId.toString(),
          type.name(),
          slug,
          displayName,
          description,
          publisher);
      return packageById(tenantId, id).orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw MarketplaceException.conflict("Package slug already exists in this tenant");
    }
  }

  @Override
  public Optional<MarketplacePackage> packageById(UUID tenantId, UUID packageId) {
    return jdbc
        .query(
            "SELECT * FROM marketplace_packages WHERE tenant_id = ? AND id = ?",
            (rs, row) -> packageRow(rs),
            tenantId.toString(),
            packageId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<MarketplacePackage> packageBySlug(UUID tenantId, String slug) {
    return jdbc
        .query(
            "SELECT * FROM marketplace_packages WHERE tenant_id = ? AND slug = ?",
            (rs, row) -> packageRow(rs),
            tenantId.toString(),
            slug)
        .stream()
        .findFirst();
  }

  @Override
  public List<MarketplacePackage> listPackages(
      UUID tenantId, PackageType type, PackageState state, int limit) {
    return jdbc.query(
        """
        SELECT * FROM marketplace_packages
        WHERE tenant_id = ? AND (? IS NULL OR package_type = ?) AND (? IS NULL OR state = ?)
        ORDER BY updated_at DESC, id DESC LIMIT ?
        """,
        (rs, row) -> packageRow(rs),
        tenantId.toString(),
        type == null ? null : type.name(),
        type == null ? null : type.name(),
        state == null ? null : state.name(),
        state == null ? null : state.name(),
        limit);
  }

  @Override
  public List<MarketplacePackage> listPublicPackages(PackageType type, int limit) {
    return jdbc.query(
        """
        SELECT p.* FROM marketplace_packages p
        WHERE p.state = 'PUBLISHED' AND (? IS NULL OR p.package_type = ?)
          AND EXISTS (
            SELECT 1 FROM marketplace_package_versions v
            WHERE v.tenant_id = p.tenant_id AND v.package_id = p.id AND v.state = 'PUBLISHED'
          )
        ORDER BY p.updated_at DESC, p.id DESC LIMIT ?
        """,
        (rs, row) -> packageRow(rs),
        type == null ? null : type.name(),
        type == null ? null : type.name(),
        limit);
  }

  @Override
  @Transactional
  public PackageVersion createVersion(
      UUID tenantId,
      UUID packageId,
      String version,
      String artifactUri,
      String sha256,
      String runtime,
      Map<String, Object> manifest) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          """
          INSERT INTO marketplace_package_versions
             (id, tenant_id, package_id, version, artifact_uri, sha256, runtime, manifest_json,
              state, revision, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', 0, CURRENT_TIMESTAMP(6))
          """,
          id.toString(),
          tenantId.toString(),
          packageId.toString(),
          version,
          artifactUri,
          sha256,
          runtime,
          json(manifest));
      return version(tenantId, packageId, id).orElseThrow();
    } catch (DuplicateKeyException exception) {
      throw MarketplaceException.conflict("Package version already exists");
    } catch (JsonProcessingException exception) {
      throw MarketplaceException.invalid("Manifest cannot be serialized");
    }
  }

  @Override
  public Optional<PackageVersion> version(UUID tenantId, UUID packageId, UUID versionId) {
    return jdbc
        .query(
            "SELECT * FROM marketplace_package_versions WHERE tenant_id = ? AND package_id = ? AND id = ?",
            (rs, row) -> versionRow(rs),
            tenantId.toString(),
            packageId.toString(),
            versionId.toString())
        .stream()
        .findFirst();
  }

  @Override
  public List<PackageVersion> versions(UUID tenantId, UUID packageId, int limit) {
    return jdbc.query(
        "SELECT * FROM marketplace_package_versions WHERE tenant_id = ? AND package_id = ? "
            + "ORDER BY created_at DESC, id DESC LIMIT ?",
        (rs, row) -> versionRow(rs),
        tenantId.toString(),
        packageId.toString(),
        limit);
  }

  @Override
  public boolean updateVersionState(
      UUID tenantId,
      UUID packageId,
      UUID versionId,
      PackageState state,
      String reviewNote,
      long expectedRevision) {
    return jdbc.update(
            """
        UPDATE marketplace_package_versions
        SET state = ?, review_note = ?, revision = revision + 1,
            reviewed_at = CASE WHEN ? = 'REVIEWED' THEN CURRENT_TIMESTAMP(6) ELSE reviewed_at END,
            published_at = CASE WHEN ? = 'PUBLISHED' THEN CURRENT_TIMESTAMP(6) ELSE published_at END
        WHERE tenant_id = ? AND package_id = ? AND id = ? AND revision = ?
        """,
            state.name(),
            reviewNote,
            state.name(),
            state.name(),
            tenantId.toString(),
            packageId.toString(),
            versionId.toString(),
            expectedRevision)
        == 1;
  }

  @Override
  public boolean updatePackageState(UUID tenantId, UUID packageId, PackageState state) {
    return jdbc.update(
            "UPDATE marketplace_packages SET state = ?, revision = revision + 1, "
                + "updated_at = CURRENT_TIMESTAMP(6) WHERE tenant_id = ? AND id = ?",
            state.name(),
            tenantId.toString(),
            packageId.toString())
        == 1;
  }

  @Override
  public boolean hasPublishedVersion(UUID tenantId, UUID packageId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM marketplace_package_versions "
                + "WHERE tenant_id = ? AND package_id = ? AND state = 'PUBLISHED'",
            Integer.class,
            tenantId.toString(),
            packageId.toString());
    return count != null && count > 0;
  }

  private MarketplacePackage packageRow(ResultSet rs) throws SQLException {
    return new MarketplacePackage(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("tenant_id")),
        PackageType.valueOf(rs.getString("package_type")),
        rs.getString("slug"),
        rs.getString("display_name"),
        rs.getString("description"),
        rs.getString("publisher"),
        PackageState.valueOf(rs.getString("state")),
        rs.getLong("revision"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private PackageVersion versionRow(ResultSet rs) throws SQLException {
    try {
      return new PackageVersion(
          UUID.fromString(rs.getString("id")),
          UUID.fromString(rs.getString("tenant_id")),
          UUID.fromString(rs.getString("package_id")),
          rs.getString("version"),
          rs.getString("artifact_uri"),
          rs.getString("sha256"),
          rs.getString("runtime"),
          mapper.readValue(rs.getString("manifest_json"), Map.class),
          PackageState.valueOf(rs.getString("state")),
          rs.getLong("revision"),
          rs.getString("review_note"),
          rs.getTimestamp("created_at").toInstant(),
          instant(rs, "reviewed_at"),
          instant(rs, "published_at"));
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw MarketplaceException.invalid("Stored package manifest is invalid");
    }
  }

  private String json(Object value) throws JsonProcessingException {
    return mapper
        .writer()
        .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .writeValueAsString(value);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
