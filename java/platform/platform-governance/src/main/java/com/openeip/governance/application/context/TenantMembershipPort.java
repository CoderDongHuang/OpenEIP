package com.openeip.governance.application.context;

import com.openeip.governance.domain.context.TenantMembership;
import java.util.Optional;
import java.util.UUID;

/** Looks up the active membership selected by the server for an authenticated principal. */
public interface TenantMembershipPort {
  Optional<TenantMembership> findActiveByPrincipal(UUID principalId);
}
