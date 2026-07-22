package com.openeip.knowledge.domain.repository;

import com.openeip.knowledge.domain.entity.KnowledgeBaseMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseMemberRepository extends JpaRepository<KnowledgeBaseMember, String> {
  Optional<KnowledgeBaseMember> findByTenantIdAndKnowledgeBaseIdAndUserId(
      String tenantId, String knowledgeBaseId, String userId);
}
