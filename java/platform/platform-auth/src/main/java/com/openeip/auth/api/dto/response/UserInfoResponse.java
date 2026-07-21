package com.openeip.auth.api.dto.response;

import com.openeip.auth.domain.entity.Permission;
import com.openeip.auth.domain.entity.Role;
import com.openeip.auth.domain.entity.User;
import java.util.Set;
import java.util.stream.Collectors;

/** Current user and effective database-backed authorities. */
public record UserInfoResponse(
    String id, String username, String email, Set<String> roles, Set<String> permissions) {

  public UserInfoResponse {
    roles = Set.copyOf(roles);
    permissions = Set.copyOf(permissions);
  }

  public static UserInfoResponse from(User user) {
    Set<String> roles =
        user.getRoles().stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
    Set<String> permissions =
        user.getRoles().stream()
            .flatMap(role -> role.getPermissions().stream())
            .map(Permission::getCode)
            .collect(Collectors.toUnmodifiableSet());
    return new UserInfoResponse(
        user.getId(), user.getUsername(), user.getEmail(), roles, permissions);
  }
}
