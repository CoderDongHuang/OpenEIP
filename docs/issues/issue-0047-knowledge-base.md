# Issue #47: Knowledge Base Control Plane

## Scope

Deliver tenant/member-scoped knowledge-base CRUD, document associations, a validated processing state
machine, and durable `eventId` de-duplication in the Java control plane.

## Acceptance

- API and database contracts are versioned and tested.
- OWNER/EDITOR/VIEWER boundaries prevent IDOR and cross-tenant access.
- Parsed and embedding completion events are idempotent and transactional.
- MySQL migration/rollback, benchmark, security review, and six quality gates pass.
- Broker adapter absence is explicit; no Kafka evidence is fabricated.
