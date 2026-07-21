package com.openeip.auth.domain.repository;

import com.openeip.auth.domain.entity.Permission;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

/** Permission reference-data repository. */
public interface PermissionRepository extends JpaRepository<Permission, String> {

  Optional<Permission> findByCode(String code);

  List<Permission> findAllByCodeIn(Set<String> codes);
}
