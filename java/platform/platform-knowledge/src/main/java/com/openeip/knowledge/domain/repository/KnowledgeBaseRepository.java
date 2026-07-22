package com.openeip.knowledge.domain.repository;

import com.openeip.knowledge.domain.entity.KnowledgeBase;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, String> {
  @Query(
      """
      select b from KnowledgeBase b join KnowledgeBaseMember m on m.knowledgeBaseId = b.id
      where b.tenantId = :tenant and m.tenantId = :tenant and m.userId = :user
        and b.deletedAt is null
      """)
  Page<KnowledgeBase> findAccessible(
      @Param("tenant") String tenant, @Param("user") String user, Pageable pageable);

  Optional<KnowledgeBase> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);

  boolean existsByTenantIdAndOwnerIdAndNameAndDeletedAtIsNull(
      String tenantId, String ownerId, String name);

  boolean existsByTenantIdAndOwnerIdAndNameAndIdNotAndDeletedAtIsNull(
      String tenantId, String ownerId, String name, String id);
}
