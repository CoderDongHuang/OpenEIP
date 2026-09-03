package com.openeip.governance.domain.quota;

import java.math.BigDecimal;

/** Validated token, cost, request, and concurrency limits for one quota policy. */
public record QuotaLimits(
    Long tokenLimit, BigDecimal costLimit, Long requestLimit, Integer concurrencyLimit) {

  public QuotaLimits {
    if (tokenLimit == null
        && costLimit == null
        && requestLimit == null
        && concurrencyLimit == null) {
      throw new IllegalArgumentException("at least one quota limit is required");
    }
    if (tokenLimit != null && tokenLimit <= 0) {
      throw new IllegalArgumentException("tokenLimit must be positive");
    }
    if (costLimit != null
        && (costLimit.signum() <= 0
            || costLimit.scale() > 6
            || costLimit.precision() - costLimit.scale() > 14)) {
      throw new IllegalArgumentException("costLimit must fit DECIMAL(20,6) and be positive");
    }
    if (requestLimit != null && requestLimit <= 0) {
      throw new IllegalArgumentException("requestLimit must be positive");
    }
    if (concurrencyLimit != null && concurrencyLimit <= 0) {
      throw new IllegalArgumentException("concurrencyLimit must be positive");
    }
  }
}
