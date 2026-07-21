package com.openeip.auth.domain.repository;

import com.openeip.auth.domain.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Refresh token registry with an explicit row lock for single-use rotation. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select token from RefreshToken token join fetch token.user where token.tokenHash = :hash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);
}
