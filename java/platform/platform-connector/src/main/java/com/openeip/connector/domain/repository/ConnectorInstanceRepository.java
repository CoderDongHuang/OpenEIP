package com.openeip.connector.domain.repository;

import com.openeip.connector.domain.entity.ConnectorInstance;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConnectorInstanceRepository extends JpaRepository<ConnectorInstance, String> {
  @Query(
      """
      select c from ConnectorInstance c
      where c.tenantId = :tenant and c.ownerId = :owner and c.deletedAt is null
      """)
  Page<ConnectorInstance> findOwned(
      @Param("tenant") String tenant, @Param("owner") String owner, Pageable pageable);

  Optional<ConnectorInstance> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);

  boolean existsByTenantIdAndOwnerIdAndNameAndDeletedAtIsNull(
      String tenantId, String ownerId, String name);

  boolean existsByTenantIdAndOwnerIdAndNameAndIdNotAndDeletedAtIsNull(
      String tenantId, String ownerId, String name, String id);
}
