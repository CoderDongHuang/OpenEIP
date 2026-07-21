package com.openeip.auth.domain.entity;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PreUpdate;
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
import org.hibernate.annotations.SQLRestriction;

/** User credential and current account state. */
@Entity
@Table(name = "auth_users")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Hibernate manages the mutable role association through this aggregate.")
public class User {

  @Id
  @Column(length = 36)
  @Builder.Default
  private String id = UUID.randomUUID().toString();

  @Column(nullable = false, unique = true, length = 64)
  private String username;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(nullable = false, length = 255)
  private String password;

  @Column(nullable = false)
  @Builder.Default
  private boolean isActive = true;

  @Column(nullable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  @Builder.Default
  private Instant updatedAt = Instant.now();

  private Instant deletedAt;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "auth_user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  @Builder.Default
  private Set<Role> roles = new HashSet<>();

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
