package com.openeip.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Creates an RBAC role from existing permission codes. */
public record CreateRoleRequest(
    @NotBlank @Pattern(regexp = "^ROLE_[A-Z0-9_]{2,59}$") String name,
    @Size(max = 255) String description,
    @NotNull @Size(max = 100) Set<@NotBlank @Size(max = 128) String> permissionCodes) {

  public CreateRoleRequest {
    permissionCodes = permissionCodes == null ? null : Set.copyOf(permissionCodes);
  }
}
