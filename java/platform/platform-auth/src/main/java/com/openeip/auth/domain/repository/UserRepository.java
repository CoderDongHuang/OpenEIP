package com.openeip.auth.domain.repository;

import com.openeip.auth.domain.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** User aggregate repository. */
public interface UserRepository extends JpaRepository<User, String> {

  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<User> findByUsername(String username);

  Optional<User> findByEmail(String email);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<User> findById(String id);
}
