package com.openeip.chat.domain.repository;

import com.openeip.chat.domain.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
  List<ChatMessage> findAllByTenantIdAndSessionIdAndOwnerIdOrderByMessageIndexAsc(
      String tenantId, String sessionId, String ownerId);
}
