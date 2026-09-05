package com.openeip.governance.application.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditAppendResult;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.domain.quota.QuotaAdmissionRequest;
import com.openeip.governance.domain.quota.QuotaConsumption;
import com.openeip.governance.domain.quota.QuotaDecisionOutcome;
import com.openeip.governance.domain.quota.QuotaLimits;
import com.openeip.governance.domain.quota.QuotaPolicy;
import com.openeip.governance.domain.quota.QuotaReservation;
import com.openeip.governance.domain.quota.QuotaReservationRegistration;
import com.openeip.governance.domain.quota.QuotaWindow;
import com.openeip.governance.domain.quota.QuotaWindowType;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuotaEnforcementServiceTest {
  private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID POLICY_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final UUID EXECUTION = UUID.fromString("44444444-4444-4444-8444-444444444444");
  private static final UUID PRINCIPAL = UUID.fromString("55555555-5555-4555-8555-555555555555");
  private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

  private FakePort port;
  private QuotaEnforcementService service;
  private AuditService audit;
  private AtomicInteger auditCount;

  @BeforeEach
  void setUp() {
    port = new FakePort(policy(QuotaWindowType.DAILY));
    auditCount = new AtomicInteger();
    audit =
        new AuditService(
            command -> {
              auditCount.incrementAndGet();
              return new AuditAppendResult(null, false);
            });
    service = new QuotaEnforcementService(port, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    bind(TENANT, "policy-1");
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void allowsExactBoundaryAndUsesServerDerivedUtcWindow() {
    port.consumption = new QuotaConsumption(90, new BigDecimal("9.000000"), 9, 1);

    var result = service.authorize(request("quota-request-0001", 10, "1.000000", 1));

    assertThat(result.allowed()).isTrue();
    assertThat(result.errorCode()).isNull();
    assertThat(port.lastRegistration.window().start())
        .isEqualTo(Instant.parse("2026-09-05T00:00:00Z"));
    assertThat(port.lastRegistration.window().end())
        .isEqualTo(Instant.parse("2026-09-06T00:00:00Z"));
    assertThat(port.lastRegistration.admittedAt()).isEqualTo(NOW);
    assertThat(auditCount).hasValue(1);
  }

  @Test
  void recordsAndReturnsStableDenial() {
    port.consumption = new QuotaConsumption(100, new BigDecimal("9.000000"), 9, 1);

    var result = service.authorize(request("quota-request-0002", 1, "0.000000", 0));

    assertThat(result.allowed()).isFalse();
    assertThat(result.errorCode()).isEqualTo("GOV-B-001");
    assertThat(port.lastRegistration.decision()).isEqualTo(QuotaDecisionOutcome.DENY);
    assertThat(auditCount).hasValue(1);
  }

  @Test
  void returnsIdenticalRetryAndRejectsChangedFacts() {
    var original = service.authorize(request("quota-request-0003", 5, "0.500000", 1));
    var duplicate = service.authorize(request("quota-request-0003", 5, "0.500000", 1));

    assertThat(duplicate.duplicate()).isTrue();
    assertThat(duplicate.reservation().id()).isEqualTo(original.reservation().id());
    assertThatThrownBy(() -> service.authorize(request("quota-request-0003", 6, "0.500000", 1)))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-I-001"));
    assertThat(port.reservations).hasSize(1);
    assertThat(auditCount).hasValue(1);
  }

  @Test
  void returnsOriginalDecisionAfterLeaseExpiryAndPolicyContextChange() {
    var request =
        new QuotaAdmissionRequest(
            TENANT,
            POLICY_ID,
            EXECUTION,
            "quota-request-expired",
            1,
            BigDecimal.ZERO,
            1,
            NOW.plusSeconds(1));
    var original = service.authorize(request);
    bind(TENANT, "policy-new");
    var laterService =
        new QuotaEnforcementService(port, audit, Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));

    var duplicate = laterService.authorize(request);

    assertThat(duplicate.duplicate()).isTrue();
    assertThat(duplicate.reservation().id()).isEqualTo(original.reservation().id());
    assertThat(auditCount).hasValue(1);
  }

  @Test
  void rejectsTenantPolicyVersionAndLeaseBoundaryViolations() {
    bind(OTHER_TENANT, "policy-1");
    assertThatThrownBy(() -> service.authorize(request("quota-request-0004", 1, "0", 0)))
        .isInstanceOf(GovernanceCatalogException.class);

    bind(TENANT, "policy-stale");
    assertThatThrownBy(() -> service.authorize(request("quota-request-0005", 1, "0", 0)))
        .isInstanceOf(GovernanceCatalogException.class);

    bind(TENANT, "policy-1");
    assertThatThrownBy(
            () ->
                service.authorize(
                    new QuotaAdmissionRequest(
                        TENANT,
                        POLICY_ID,
                        EXECUTION,
                        "quota-request-0006",
                        1,
                        BigDecimal.ZERO,
                        0,
                        NOW.plusSeconds(24 * 60 * 60 + 1))))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-V-001"));
  }

  @Test
  void releasesAllowedReservationOnceAndKeepsDeniedDecisionImmutable() {
    var allowed = service.authorize(request("quota-request-0007", 1, "0", 1));
    var released = service.release(allowed.reservation().id());
    var retried = service.release(allowed.reservation().id());

    assertThat(released.releasedAt()).isEqualTo(NOW);
    assertThat(retried.releasedAt()).isEqualTo(NOW);
    assertThat(auditCount).hasValue(2);

    port.consumption = new QuotaConsumption(100, BigDecimal.ZERO, 0, 0);
    var denied = service.authorize(request("quota-request-0008", 1, "0", 0));
    assertThatThrownBy(() -> service.release(denied.reservation().id()))
        .isInstanceOf(GovernanceCatalogException.class);
  }

  private QuotaAdmissionRequest request(String key, long tokens, String cost, int concurrency) {
    return new QuotaAdmissionRequest(
        TENANT,
        POLICY_ID,
        EXECUTION,
        key,
        tokens,
        new BigDecimal(cost),
        concurrency,
        NOW.plusSeconds(3600));
  }

  private QuotaPolicy policy(QuotaWindowType type) {
    return new QuotaPolicy(
        POLICY_ID,
        TENANT,
        "runtime-quota",
        "policy-1",
        new QuotaLimits(100L, new BigDecimal("10.000000"), 10L, 2),
        type,
        0,
        NOW,
        NOW);
  }

  private void bind(UUID tenantId, String policyVersion) {
    TenantContextHolder.bind(
        new TenantContext(
            tenantId,
            null,
            PRINCIPAL,
            UUID.randomUUID(),
            Set.of("ROLE_OPERATOR"),
            policyVersion,
            "request-quota-enforcement",
            "0123456789abcdef0123456789abcdef",
            GovernanceScope.TENANT,
            Instant.parse("2099-01-01T00:00:00Z")));
  }

  private static final class FakePort implements QuotaEnforcementPort {
    private final QuotaPolicy policy;
    private final Map<UUID, QuotaReservation> reservations = new LinkedHashMap<>();
    private QuotaConsumption consumption = QuotaConsumption.ZERO;
    private QuotaReservationRegistration lastRegistration;

    private FakePort(QuotaPolicy policy) {
      this.policy = policy;
    }

    @Override
    public Optional<QuotaPolicy> lockPolicy(UUID tenantId, UUID quotaPolicyId) {
      return policy.tenantId().equals(tenantId) && policy.id().equals(quotaPolicyId)
          ? Optional.of(policy)
          : Optional.empty();
    }

    @Override
    public Optional<QuotaReservation> reservationByIdempotency(
        UUID tenantId, UUID quotaPolicyId, String idempotencyKey) {
      return reservations.values().stream()
          .filter(value -> value.tenantId().equals(tenantId))
          .filter(value -> value.quotaPolicyId().equals(quotaPolicyId))
          .filter(value -> value.idempotencyKey().equals(idempotencyKey))
          .findFirst();
    }

    @Override
    public Optional<QuotaReservation> reservation(UUID tenantId, UUID reservationId) {
      return Optional.ofNullable(reservations.get(reservationId))
          .filter(value -> value.tenantId().equals(tenantId));
    }

    @Override
    public QuotaConsumption consumption(
        UUID tenantId, UUID quotaPolicyId, UUID executionId, QuotaWindow window, Instant now) {
      return consumption;
    }

    @Override
    public QuotaReservation append(QuotaReservationRegistration registration) {
      lastRegistration = registration;
      UUID id = UUID.randomUUID();
      var request = registration.request();
      var reservation =
          new QuotaReservation(
              id,
              request.tenantId(),
              request.quotaPolicyId(),
              request.executionId(),
              request.idempotencyKey(),
              registration.policyVersion(),
              registration.window(),
              request.requestedTokens(),
              request.requestedCost(),
              request.concurrencyUnits(),
              registration.observed(),
              registration.decision(),
              registration.requestId(),
              registration.traceId(),
              request.expiresAt(),
              null,
              registration.admittedAt());
      reservations.put(id, reservation);
      return reservation;
    }

    @Override
    public boolean release(UUID tenantId, UUID reservationId, Instant releasedAt) {
      QuotaReservation value = reservations.get(reservationId);
      if (value == null || !value.tenantId().equals(tenantId) || value.releasedAt() != null) {
        return false;
      }
      reservations.put(
          reservationId,
          new QuotaReservation(
              value.id(),
              value.tenantId(),
              value.quotaPolicyId(),
              value.executionId(),
              value.idempotencyKey(),
              value.policyVersion(),
              value.window(),
              value.requestedTokens(),
              value.requestedCost(),
              value.concurrencyUnits(),
              value.observed(),
              value.decision(),
              value.requestId(),
              value.traceId(),
              value.expiresAt(),
              releasedAt,
              value.createdAt()));
      return true;
    }
  }
}
