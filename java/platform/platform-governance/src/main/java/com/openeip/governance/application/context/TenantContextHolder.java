package com.openeip.governance.application.context;

import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.shared.exception.GovernanceAuthorizationException;
import java.util.Optional;

/** Binds the validated context to the current request thread and prevents context leakage. */
public final class TenantContextHolder {
  private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

  private TenantContextHolder() {}

  public static void bind(TenantContext context) {
    CURRENT.set(context);
  }

  public static Optional<TenantContext> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static TenantContext required() {
    return current()
        .orElseThrow(
            () -> GovernanceAuthorizationException.invalidContext("Tenant context is not bound"));
  }

  public static void clear() {
    CURRENT.remove();
  }
}
