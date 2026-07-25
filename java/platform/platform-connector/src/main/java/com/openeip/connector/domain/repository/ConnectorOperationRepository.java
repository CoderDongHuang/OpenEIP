package com.openeip.connector.domain.repository;

import com.openeip.connector.domain.entity.ConnectorOperation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectorOperationRepository extends JpaRepository<ConnectorOperation, String> {
  Optional<ConnectorOperation> findByTenantIdAndConnectorIdAndOperationTypeAndIdempotencyKey(
      String tenantId, String connectorId, String operationType, String idempotencyKey);
}
