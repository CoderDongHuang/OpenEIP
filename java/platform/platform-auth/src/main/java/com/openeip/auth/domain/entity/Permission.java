package com.openeip.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fine-grained permission assigned through roles. */
@Entity
@Table(name = "auth_permissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

  @Id
  @Column(length = 36)
  @Builder.Default
  private String id = UUID.randomUUID().toString();

  @Column(nullable = false, unique = true, length = 128)
  private String code;

  @Column(length = 255)
  private String description;

  @Column(nullable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}
