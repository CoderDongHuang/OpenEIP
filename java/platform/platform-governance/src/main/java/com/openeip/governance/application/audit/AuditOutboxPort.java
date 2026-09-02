package com.openeip.governance.application.audit;

import com.openeip.governance.domain.audit.OutboxEntry;
import java.util.List;
import java.util.UUID;

/** Delivery port for the governance transactional outbox. */
public interface AuditOutboxPort {
  List<OutboxEntry> claimPending(UUID tenantId, int limit);

  boolean markDelivered(UUID tenantId, UUID eventId);

  boolean markRetryable(UUID tenantId, UUID eventId);

  boolean quarantine(UUID tenantId, UUID eventId);
}
