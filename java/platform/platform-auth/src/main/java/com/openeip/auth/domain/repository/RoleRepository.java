package com.openeip.auth.domain.repository;

import com.openeip.auth.domain.entity.Role;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Role aggregate repository. */
public interface RoleRepository extends JpaRepository<Role, String> {

  @EntityGraph(attributePaths = "permissions")
  Optional<Role> findByName(String name);

  @Override
  @EntityGraph(attributePaths = "permissions")
  List<Role> findAll();

  @EntityGraph(attributePaths = "permissions")
  List<Role> findAllById(Iterable<String> ids);

  boolean existsByName(String name);

  @EntityGraph(attributePaths = "permissions")
  List<Role> findAllByNameIn(Set<String> names);
}
