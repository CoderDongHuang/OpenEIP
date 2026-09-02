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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the server-selected active membership without accepting a client tenant identifier. */
@Repository
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "JdbcTemplate and ObjectMapper are application-scoped collaborators.")
public class JdbcTenantMembershipAdapter implements TenantMembershipPort {
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
