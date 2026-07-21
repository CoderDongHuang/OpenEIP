package com.openeip.auth.domain.entity;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persistent state used to rotate and revoke refresh tokens. */
@Entity
@Table(name = "auth_refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "The user reference is a Hibernate-managed aggregate association.")
public class RefreshToken {

  @Id
  @Column(length = 36)
  @Builder.Default
  private String id = UUID.randomUUID().toString();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant usedAt;

  private Instant revokedAt;

  @Column(nullable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();

  public boolean isConsumable(Instant now) {
    return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
  }
}
