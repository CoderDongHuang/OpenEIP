package com.openeip.governance.application.quota;

import com.openeip.governance.domain.quota.QuotaConsumption;
import com.openeip.governance.domain.quota.QuotaPolicy;
import com.openeip.governance.domain.quota.QuotaReservation;
import com.openeip.governance.domain.quota.QuotaReservationRegistration;
import com.openeip.governance.domain.quota.QuotaWindow;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Transactional persistence boundary for runtime quota admission and release. */
public interface QuotaEnforcementPort {
  Optional<QuotaPolicy> lockPolicy(UUID tenantId, UUID quotaPolicyId);

  Optional<QuotaReservation> reservationByIdempotency(
      UUID tenantId, UUID quotaPolicyId, String idempotencyKey);

  Optional<QuotaReservation> reservation(UUID tenantId, UUID reservationId);

  QuotaConsumption consumption(
      UUID tenantId, UUID quotaPolicyId, UUID executionId, QuotaWindow window, Instant now);

  QuotaReservation append(QuotaReservationRegistration registration);

  boolean release(UUID tenantId, UUID reservationId, Instant releasedAt);
}
