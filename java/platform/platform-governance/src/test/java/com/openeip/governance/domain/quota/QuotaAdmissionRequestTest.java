package com.openeip.governance.domain.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuotaAdmissionRequestTest {
  private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID POLICY = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID EXECUTION = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final Instant EXPIRES_AT = Instant.parse("2026-09-05T13:00:00Z");

  @Test
  void rejectsUnsafeOrUnboundedIdempotencyKeys() {
    assertThatThrownBy(() -> request("short", 1, "1.000000", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> request("unsafe key with spaces", 1, "1.000000", 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNegativeOrOutOfRangeReservations() {
    assertThatThrownBy(() -> request("quota-request-0001", -1, "1.000000", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> request("quota-request-0002", 1, "-0.000001", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> request("quota-request-0003", 1, "1.0000001", 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> request("quota-request-0004", 1, "1.000000", 2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void canonicalizesLeaseExpiryToDatabasePrecision() {
    var request =
        new QuotaAdmissionRequest(
            TENANT,
            POLICY,
            EXECUTION,
            "quota-request-0005",
            1,
            BigDecimal.ZERO,
            0,
            Instant.parse("2026-09-05T13:00:00.123456789Z"));

    assertThat(request.expiresAt()).isEqualTo("2026-09-05T13:00:00.123456Z");
  }

  private QuotaAdmissionRequest request(
      String key, long tokens, String cost, int concurrencyUnits) {
    return new QuotaAdmissionRequest(
        TENANT, POLICY, EXECUTION, key, tokens, new BigDecimal(cost), concurrencyUnits, EXPIRES_AT);
  }
}
