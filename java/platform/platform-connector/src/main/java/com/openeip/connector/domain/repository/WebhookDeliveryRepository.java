package com.openeip.connector.domain.repository;

import com.openeip.connector.domain.entity.WebhookDelivery;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, String> {
  Optional<WebhookDelivery> findByTenantIdAndConnectorIdAndEventId(
      String tenantId, String connectorId, String eventId);

  List<WebhookDelivery> findByTenantIdAndConnectorIdOrderByReceivedAtDesc(
      String tenantId, String connectorId, Pageable pageable);
}
