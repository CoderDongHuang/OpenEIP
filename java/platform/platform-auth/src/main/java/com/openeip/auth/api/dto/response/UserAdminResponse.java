package com.openeip.auth.api.dto.response;

import com.openeip.auth.domain.entity.User;
import java.time.Instant;
import java.util.Set;

/** Administrative account view without credential material. */
public record UserAdminResponse(
    String id,
    String username,
    String email,
    boolean active,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt) {
  public UserAdminResponse {
    roles = Set.copyOf(roles);
  }

  public static UserAdminResponse from(User user) {
    return new UserAdminResponse(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.isActive(),
        user.getRoles().stream()
            .map(role -> role.getName())
            .collect(java.util.stream.Collectors.toUnmodifiableSet()),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
