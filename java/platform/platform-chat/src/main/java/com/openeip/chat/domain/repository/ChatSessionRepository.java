package com.openeip.chat.domain.repository;

import com.openeip.chat.domain.entity.ChatSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
  Optional<ChatSession> findByIdAndTenantIdAndOwnerId(String id, String tenantId, String ownerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from ChatSession s where s.id = :id and s.tenantId = :tenant and s.ownerId = :owner")
  Optional<ChatSession> findOwnedForUpdate(
      @Param("id") String id, @Param("tenant") String tenantId, @Param("owner") String ownerId);
}
