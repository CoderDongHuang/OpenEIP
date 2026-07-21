package com.openeip.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Replaces all roles assigned to a user. */
public record AssignRolesRequest(
    @NotNull @Size(min = 1, max = 20)
        Set<@NotBlank @Pattern(regexp = "^ROLE_[A-Z0-9_]{2,59}$") String> roleNames) {

  public AssignRolesRequest {
    roleNames = roleNames == null ? null : Set.copyOf(roleNames);
  }
}
