package com.openeip.workflow.domain.repository;

import com.openeip.workflow.domain.entity.WorkflowDefinition;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, String> {
  @Query(
      """
      select w from WorkflowDefinition w join WorkflowMember m on m.workflowId = w.id
      where w.tenantId = :tenant and m.tenantId = :tenant and m.userId = :user
        and w.deletedAt is null
      """)
  Page<WorkflowDefinition> findAccessible(
      @Param("tenant") String tenant, @Param("user") String user, Pageable pageable);

  Optional<WorkflowDefinition> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);

  boolean existsByTenantIdAndOwnerIdAndNameAndDeletedAtIsNull(
      String tenantId, String ownerId, String name);

  boolean existsByTenantIdAndOwnerIdAndNameAndIdNotAndDeletedAtIsNull(
      String tenantId, String ownerId, String name, String id);
}
