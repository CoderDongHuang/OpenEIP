package com.openeip.auth.domain.entity;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Named RBAC role and its permission set. */
@Entity
@Table(name = "auth_roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Hibernate manages the mutable permission association through this aggregate.")
public class Role {

  @Id
  @Column(length = 36)
  @Builder.Default
  private String id = UUID.randomUUID().toString();

  @Column(nullable = false, unique = true, length = 64)
  private String name;

  @Column(length = 255)
  private String description;

  @Column(nullable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "auth_role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  @Builder.Default
  private Set<Permission> permissions = new HashSet<>();
}
