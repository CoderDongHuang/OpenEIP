package com.openeip.governance.application.usage;

import com.openeip.governance.domain.usage.PricingSnapshot;
import com.openeip.governance.domain.usage.PricingSnapshotRegistration;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for immutable provider/model pricing snapshots. */
public interface PricingSnapshotPort {
  PricingSnapshot create(PricingSnapshotRegistration registration);

  Optional<PricingSnapshot> pricingSnapshot(UUID tenantId, UUID pricingSnapshotId);
}
