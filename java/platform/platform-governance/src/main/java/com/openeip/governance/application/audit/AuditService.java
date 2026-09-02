package com.openeip.governance.application.audit;

import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditAppendCommand;
import com.openeip.governance.domain.audit.AuditAppendResult;
import com.openeip.governance.domain.audit.AuditSummarySanitizer;
import com.openeip.governance.shared.exception.GovernanceAuditException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for tenant-checked, sanitized, transactional audit writes. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "The persistence port is an application-scoped collaborator.")
public class AuditService {
  private final AuditAppendPort appends;

  public AuditService(AuditAppendPort appends) {
    this.appends = appends;
  }

  @Transactional
  public AuditAppendResult append(AuditAppendCommand command) {
    var context = TenantContextHolder.required();
    if (context.expiredAt(java.time.Instant.now())
        || !context.tenantId().equals(command.tenantId())
        || !context.principalId().equals(command.principalId())) {
      throw GovernanceAuditException.invalid("Audit command does not match the active context");
    }
    try {
      var sanitized =
          new AuditAppendCommand(
              command.eventId(),
              command.tenantId(),
              command.principalId(),
              command.action(),
              command.resourceType(),
              command.resourceId(),
              command.outcome(),
              command.requestId(),
              command.traceId(),
              command.policyVersion(),
              command.schemaVersion(),
              command.occurredAt(),
              command.retentionDeadline(),
              AuditSummarySanitizer.sanitize(command.summary()));
      return appends.append(sanitized);
    } catch (IllegalArgumentException exception) {
      throw GovernanceAuditException.invalid(exception.getMessage());
    }
  }

  public static AuditAppendCommand command(
      UUID eventId,
      UUID tenantId,
      UUID principalId,
      String action,
      String resourceType,
      String resourceId,
      com.openeip.governance.domain.audit.AuditOutcome outcome,
      String requestId,
      String traceId,
      String policyVersion,
      java.time.Instant occurredAt,
      java.util.Map<String, Object> summary) {
    return new AuditAppendCommand(
        eventId,
        tenantId,
        principalId,
        action,
        resourceType,
        resourceId,
        outcome,
        requestId,
        traceId,
        policyVersion,
        "governance.event.v1",
        occurredAt,
        null,
        summary);
  }
}
