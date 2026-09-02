package com.openeip.governance.application.audit;

import com.openeip.governance.domain.audit.AuditAppendCommand;
import com.openeip.governance.domain.audit.AuditAppendResult;

/** Persistence port for atomic audit and outbox appends. */
public interface AuditAppendPort {
  AuditAppendResult append(AuditAppendCommand command);
}
