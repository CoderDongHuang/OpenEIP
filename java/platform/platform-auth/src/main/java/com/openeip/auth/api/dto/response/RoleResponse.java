package com.openeip.auth.api.dto.response;

import com.openeip.auth.domain.entity.Permission;
import com.openeip.auth.domain.entity.Role;
import java.util.Set;
import java.util.stream.Collectors;

/** Public representation of a role. */
public record RoleResponse(String id, String name, String description, Set<String> permissions) {

  public RoleResponse {
    permissions = Set.copyOf(permissions);
  }

  public static RoleResponse from(Role role) {
    Set<String> permissions =
        role.getPermissions().stream()
            .map(Permission::getCode)
            .collect(Collectors.toUnmodifiableSet());
    return new RoleResponse(role.getId(), role.getName(), role.getDescription(), permissions);
  }
}
