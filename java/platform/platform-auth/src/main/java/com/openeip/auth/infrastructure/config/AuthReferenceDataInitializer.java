package com.openeip.auth.infrastructure.config;

import com.openeip.auth.domain.entity.Permission;
import com.openeip.auth.domain.entity.Role;
import com.openeip.auth.domain.repository.PermissionRepository;
import com.openeip.auth.domain.repository.RoleRepository;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotently creates the built-in RBAC reference data. */
@Component
public class AuthReferenceDataInitializer implements ApplicationRunner {

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;

  public AuthReferenceDataInitializer(
      RoleRepository roleRepository, PermissionRepository permissionRepository) {
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    Map<String, String> descriptions = new LinkedHashMap<>();
    descriptions.put("document:read", "Read documents");
    descriptions.put("document:write", "Create and update documents");
    descriptions.put("document:delete", "Delete documents");
    descriptions.put("knowledge:search", "Search knowledge bases");
    descriptions.put("knowledge:manage", "Manage knowledge bases");
    descriptions.put("user:manage", "Manage users and roles");

    Map<String, Permission> permissions = new LinkedHashMap<>();
    descriptions.forEach(
        (code, description) ->
            permissions.put(
                code,
                permissionRepository
                    .findByCode(code)
                    .orElseGet(
                        () ->
                            permissionRepository.save(
                                Permission.builder()
                                    .code(code)
                                    .description(description)
                                    .build()))));

    upsertRole("ROLE_ADMIN", "System administrator", Set.copyOf(permissions.values()));
    upsertRole(
        "ROLE_USER",
        "Standard user",
        Set.of(
            permissions.get("document:read"),
            permissions.get("document:write"),
            permissions.get("knowledge:search")));
    upsertRole(
        "ROLE_VIEWER",
        "Read-only user",
        Set.of(permissions.get("document:read"), permissions.get("knowledge:search")));
  }

  private void upsertRole(String name, String description, Set<Permission> permissions) {
    Role role =
        roleRepository
            .findByName(name)
            .orElseGet(() -> Role.builder().name(name).description(description).build());
    role.setDescription(description);
    role.setPermissions(new HashSet<>(permissions));
    roleRepository.save(role);
  }
}
