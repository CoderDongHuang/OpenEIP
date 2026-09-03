package com.openeip.governance.application.quota;

import com.openeip.governance.domain.quota.QuotaPolicy;
import com.openeip.governance.domain.quota.QuotaPolicyRegistration;
import com.openeip.governance.domain.quota.QuotaPolicyUpdate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for revisioned tenant quota policy metadata. */
public interface QuotaPolicyPort {
  QuotaPolicy create(QuotaPolicyRegistration registration, String policyVersion);

  Optional<QuotaPolicy> quota(UUID tenantId, UUID quotaPolicyId);

  List<QuotaPolicy> quotas(UUID tenantId, int limit);

  boolean update(QuotaPolicyUpdate update, String policyVersion);
}
