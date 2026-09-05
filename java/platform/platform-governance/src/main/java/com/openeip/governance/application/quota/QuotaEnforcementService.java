package com.openeip.governance.application.quota;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.quota.QuotaAdmissionRequest;
import com.openeip.governance.domain.quota.QuotaAdmissionResult;
import com.openeip.governance.domain.quota.QuotaConsumption;
import com.openeip.governance.domain.quota.QuotaDecisionOutcome;
import com.openeip.governance.domain.quota.QuotaPolicy;
import com.openeip.governance.domain.quota.QuotaReservation;
import com.openeip.governance.domain.quota.QuotaReservationRegistration;
import com.openeip.governance.domain.quota.QuotaWindow;
import com.openeip.governance.domain.quota.QuotaWindowType;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoritative, fail-closed runtime quota admission and lease service. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Quota and audit ports are application-scoped collaborators.")
public class QuotaEnforcementService {
  private static final Duration MAX_LEASE = Duration.ofHours(24);
  private static final Instant EXECUTION_START = Instant.parse("1970-01-01T00:00:00Z");
  private static final Instant EXECUTION_END = Instant.parse("9999-12-31T23:59:59Z");

  private final QuotaEnforcementPort enforcement;
  private final AuditService audit;
  private final Clock clock;

  @Autowired
  public QuotaEnforcementService(QuotaEnforcementPort enforcement, AuditService audit) {
    this(enforcement, audit, Clock.systemUTC());
  }

  QuotaEnforcementService(QuotaEnforcementPort enforcement, AuditService audit, Clock clock) {
    this.enforcement = enforcement;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public QuotaAdmissionResult authorize(QuotaAdmissionRequest request) {
    Context context = context(request.tenantId());
    Instant admittedAt = clock.instant();
    validateLease(request.expiresAt(), admittedAt);
    QuotaPolicy policy =
        enforcement
            .lockPolicy(context.tenantId(), request.quotaPolicyId())
            .orElseThrow(
                () ->
                    GovernanceCatalogException.invalid(
                        "Quota policy was not found in this tenant"));
    if (!policy.policyVersion().equals(context.policyVersion())) {
      throw GovernanceCatalogException.conflict("Quota policy version is stale");
    }

    var existing =
        enforcement.reservationByIdempotency(
            context.tenantId(), request.quotaPolicyId(), request.idempotencyKey());
    if (existing.isPresent()) {
      QuotaReservation reservation = existing.orElseThrow();
      if (!reservation.sameFacts(request)) {
        throw GovernanceCatalogException.idempotency(
            "Quota idempotency key has different admission facts");
      }
      return result(reservation, true);
    }

    QuotaWindow window = window(policy.windowType(), admittedAt);
    QuotaConsumption observed =
        enforcement.consumption(
            context.tenantId(), policy.id(), request.executionId(), window, admittedAt);
    QuotaDecisionOutcome decision =
        observed.reserve(request).within(policy.limits())
            ? QuotaDecisionOutcome.ALLOW
            : QuotaDecisionOutcome.DENY;
    QuotaReservation reservation =
        enforcement.append(
            new QuotaReservationRegistration(
                request,
                context.policyVersion(),
                window,
                observed,
                decision,
                context.requestId(),
                context.traceId(),
                admittedAt));
    audit(context, reservation, decision == QuotaDecisionOutcome.ALLOW);
    return result(reservation, false);
  }

  @Transactional
  public QuotaReservation release(UUID reservationId) {
    Context context = context(null);
    QuotaReservation reservation =
        enforcement
            .reservation(context.tenantId(), reservationId)
            .orElseThrow(
                () ->
                    GovernanceCatalogException.invalid(
                        "Quota reservation was not found in this tenant"));
    if (reservation.decision() != QuotaDecisionOutcome.ALLOW) {
      throw GovernanceCatalogException.transition("A denied quota decision cannot be released");
    }
    if (reservation.releasedAt() == null) {
      Instant releasedAt = clock.instant();
      if (!enforcement.release(context.tenantId(), reservationId, releasedAt)) {
        reservation =
            enforcement
                .reservation(context.tenantId(), reservationId)
                .orElseThrow(
                    () ->
                        GovernanceCatalogException.invalid(
                            "Quota reservation was not found in this tenant"));
      } else {
        reservation = enforcement.reservation(context.tenantId(), reservationId).orElseThrow();
        audit(context, reservation, true);
      }
    }
    return reservation;
  }

  private QuotaAdmissionResult result(QuotaReservation reservation, boolean duplicate) {
    String error =
        reservation.decision() == QuotaDecisionOutcome.DENY
            ? GovernanceCatalogException.BUDGET_CODE
            : null;
    return new QuotaAdmissionResult(reservation, duplicate, error);
  }

  private void validateLease(Instant expiresAt, Instant admittedAt) {
    if (!expiresAt.isAfter(admittedAt) || expiresAt.isAfter(admittedAt.plus(MAX_LEASE))) {
      throw GovernanceCatalogException.invalid(
          "Quota lease must be positive and no longer than 24 hours from server time");
    }
  }

  private QuotaWindow window(QuotaWindowType type, Instant now) {
    if (type == QuotaWindowType.EXECUTION) {
      return new QuotaWindow(type, EXECUTION_START, EXECUTION_END);
    }
    ZonedDateTime current = now.atZone(ZoneOffset.UTC);
    ZonedDateTime start;
    ZonedDateTime end;
    if (type == QuotaWindowType.DAILY) {
      start = current.toLocalDate().atStartOfDay(ZoneOffset.UTC);
      end = start.plusDays(1);
    } else if (type == QuotaWindowType.WEEKLY) {
      start =
          current
              .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
              .toLocalDate()
              .atStartOfDay(ZoneOffset.UTC);
      end = start.plusWeeks(1);
    } else {
      start = current.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC);
      end = start.plusMonths(1);
    }
    return new QuotaWindow(type, start.toInstant(), end.toInstant());
  }

  private Context context(UUID expectedTenantId) {
    var value = TenantContextHolder.required();
    if (value.expiredAt(clock.instant())
        || (expectedTenantId != null && !expectedTenantId.equals(value.tenantId()))) {
      throw GovernanceCatalogException.invalid("Quota command does not match active context");
    }
    return new Context(
        value.tenantId(),
        value.principalId(),
        value.requestId(),
        value.traceId(),
        value.policyVersion());
  }

  private void audit(Context context, QuotaReservation reservation, boolean allowed) {
    audit.append(
        AuditService.command(
            UUID.randomUUID(),
            context.tenantId(),
            context.principalId(),
            reservation.releasedAt() == null
                ? "governance.quota." + reservation.decision().name().toLowerCase()
                : "governance.quota.released",
            "quota-reservation",
            reservation.id().toString(),
            allowed ? AuditOutcome.SUCCESS : AuditOutcome.DENIED,
            context.requestId(),
            context.traceId(),
            context.policyVersion(),
            clock.instant(),
            Map.of("policyDecision", reservation.decision().name())));
  }

  private record Context(
      UUID tenantId, UUID principalId, String requestId, String traceId, String policyVersion) {}
}
