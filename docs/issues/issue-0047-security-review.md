# Security Review: Issue #47 Knowledge Base

> Status: Passed | Date: 2026-07-22

| Threat | Control | Evidence |
|---|---|---|
| IDOR / tenant escape | Server-derived internal tenant; every base/document query requires membership | API test returns the same 404 for absent and inaccessible UUIDs |
| Role escalation | OWNER/EDITOR/VIEWER capability checks occur in the service transaction | update, attach, detach, and delete paths are role-tested |
| Foreign file association | Attachment requires the current owner or an authenticated platform admin | H2 lifecycle and repository ownership tests |
| State bypass | Explicit five-state transition table rejects skipped and terminal transitions | domain and event service tests |
| Event replay/collision | `(tenant_id,event_id)` unique record plus type/resource/payload fingerprint match | exact replay is no-op; changed payload is 409 |
| Forged/cross-tenant event | strict unknown-field JSON, fixed type/version/source, 64 KiB limit, external tenant UUID mapping | listener tests and Embedded Kafka test |
| Poison event / availability | two retries after the first attempt, then same-partition `.DLQ` | Embedded Kafka observes invalid JSON on `.DLQ` |
| Sensitive diagnostics | bounded uppercase failure code only; public errors omit SQL, event payload, and membership | failure-code validation and exception-advice tests |
| Batch exhaustion | API page size <= 100; event payload and chunk count are bounded by versioned contracts | validation and contract tests |

## Residual Risk

- MVP maps one configured external tenant UUID to the RFC-0002 internal logical tenant `default`.
  Multi-tenant claims and membership provisioning require a later governance migration.
- Kafka listeners are disabled by default. Enabling them requires authenticated, encrypted broker
  configuration at deployment; those broker credentials are not stored in the repository.
- The producer outbox is outside this consumer module. This PR does not claim exactly-once delivery.

Trivy 0.72.0 found 0 HIGH/CRITICAL vulnerabilities in the committed snapshot, Java fat-JAR runtime,
and Debian 13.6/Python runtime. `pip-audit` found 0. The Docusaurus development chain retains 18
moderate `uuid` advisories with no upstream fix and does not cross the release severity gate.
