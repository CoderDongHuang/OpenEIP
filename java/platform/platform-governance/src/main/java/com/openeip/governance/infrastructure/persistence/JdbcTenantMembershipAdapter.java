package com.openeip.governance.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.governance.application.context.TenantMembershipPort;
import com.openeip.governance.domain.context.TenantMembership;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Reads the server-selected active membership without accepting a client tenant identifier. */
@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JdbcTemplate and ObjectMapper are application-scoped collaborators.")
public class JdbcTenantMembershipAdapter implements TenantMembershipPort {
  private static final UUID DEFAULT_TENANT_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000001");
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcTenantMembershipAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public Optional<TenantMembership> findActiveByPrincipal(UUID principalId) {
    if (principalId == null) {
      return Optional.empty();
    }
    var memberships =
        jdbc.query(
            """
            SELECT id, tenant_id, organization_id, principal_id, roles_json, policy_version
            FROM governance_memberships
            WHERE principal_id = ? AND state = 'ACTIVE'
            ORDER BY tenant_id
            LIMIT 2
            """,
            (resultSet, rowNumber) -> toMembership(resultSet),
            principalId.toString());
    if (memberships.size() != 1) {
      return Optional.empty();
    }
    return memberships.getFirst();
  }

  @Override
  @Transactional
  public Optional<TenantMembership> provisionDefault(UUID principalId, Set<String> roles) {
    if (principalId == null || roles == null || roles.isEmpty()) {
      return Optional.empty();
    }
    Optional<TenantMembership> existing = findActiveByPrincipal(principalId);
    if (existing.isPresent()) {
      return existing;
    }
    Set<String> governanceRoles =
        roles.contains("ROLE_ADMIN")
            ? Set.of("GOVERNANCE_ADMIN", "OPERATOR", "VIEWER")
            : Set.of("VIEWER");
    try {
      jdbc.update(
          """
          INSERT INTO governance_memberships
            (id, tenant_id, organization_id, principal_id, roles_json, state,
             policy_version, revision, created_at, updated_at)
          VALUES (?, ?, NULL, ?, ?, 'ACTIVE', 'governance-v1', 0,
                  CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
          """,
          UUID.randomUUID().toString(),
          DEFAULT_TENANT_ID.toString(),
          principalId.toString(),
          rolesJson(governanceRoles));
    } catch (DuplicateKeyException ignored) {
      // A concurrent authenticated request may have provisioned the same principal.
    }
    return findActiveByPrincipal(principalId);
  }

  private String rolesJson(Set<String> roles) {
    try {
      return mapper.writeValueAsString(roles.stream().sorted().toList());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Governance roles cannot be serialized", exception);
    }
  }

  private Optional<TenantMembership> toMembership(java.sql.ResultSet resultSet) {
    try {
      JsonNode rolesNode = mapper.readTree(resultSet.getString("roles_json"));
      if (rolesNode == null || !rolesNode.isArray()) {
        return Optional.empty();
      }
      var roles = new ArrayList<String>();
      for (JsonNode role : rolesNode) {
        if (!role.isTextual() || role.textValue().isBlank()) {
          return Optional.empty();
        }
        roles.add(role.textValue());
      }
      return Optional.of(
          new TenantMembership(
              UUID.fromString(resultSet.getString("id")),
              UUID.fromString(resultSet.getString("tenant_id")),
              nullableUuid(resultSet.getString("organization_id")),
              UUID.fromString(resultSet.getString("principal_id")),
              Set.copyOf(roles),
              resultSet.getString("policy_version")));
    } catch (JsonProcessingException | SQLException | IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private static UUID nullableUuid(String value) {
    return value == null ? null : UUID.fromString(value);
  }
}
