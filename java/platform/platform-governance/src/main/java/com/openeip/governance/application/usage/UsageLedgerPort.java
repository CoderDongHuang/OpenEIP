package com.openeip.governance.application.usage;

import com.openeip.governance.domain.usage.PricingSnapshot;
import com.openeip.governance.domain.usage.UsageAppendResult;
import com.openeip.governance.domain.usage.UsageRecord;
import com.openeip.governance.domain.usage.UsageRegistration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for idempotent, immutable usage and cost records. */
public interface UsageLedgerPort {
  UsageAppendResult append(
      UsageRegistration registration, PricingSnapshot pricing, BigDecimal calculatedAmount);

  Optional<UsageRecord> usageByIdempotency(
      UUID tenantId, UUID executionId, String providerRequestId, long usageRevision);

  List<UsageRecord> usages(UUID tenantId, UUID executionId, Instant from, Instant to, int limit);

  BigDecimal totalAmount(UUID tenantId, UUID executionId, Instant from, Instant to);
}
