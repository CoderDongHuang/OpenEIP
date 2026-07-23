# Security Review: Issue #63 v0.2 Product and Release Polish

> Status: Passed | Last updated: 2026-07-24

| Threat | Control | Verification | Status |
|---|---|---|---|
| VIEWER processing bypass | Processing and retry call `getEditableDocument` before file or engine access | Service authorization unit test and Java module tests | Passed |
| Retry state abuse | Only FAILED or READY can reset; all other states return a stable conflict | Domain state-machine tests | Passed |
| Cross-user file access | Existing owner/admin file service authorization remains mandatory after knowledge authorization | Ingestion service tests and full-stack smoke | Passed |
| Failure detail exposure | Retry preserves only bounded count and stable failure code; source content and provider detail stay server-side | Domain tests and API response inspection | Passed |
| Browser injection | React text rendering only; no raw HTML API or external runtime script is introduced | Source review and desktop/mobile browser QA | Passed |
| Token disclosure | Same-origin Java paths and session-scoped token storage remain unchanged | Built-asset and Trivy secret scan | Passed |
| Vulnerable dependencies | Source, dependency, and every Compose runtime image must have no HIGH/CRITICAL finding | npm/pip audits plus Trivy source and five-image runtime scans | Passed |
| Database image supply chain | Official MySQL 8.4.10 is digest-pinned; unused mysqlsh is removed and vulnerable gosu is replaced before the sanitized filesystem is flattened | Existing-volume upgrade, MySQL contracts, release smoke, and zero-finding runtime scan | Passed |

## Residual Risk

- READY is persisted while vectors are held in Python memory. The explicit rebuild command restores
  service after restart, but automatic reconciliation requires durable vectors or background jobs.
- Browser access tokens remain in session storage under the accepted v0.2 alpha risk model.
- Retry is synchronous and bounded by current request timeouts; production cancellation and job
  scheduling remain outside this version.
- The hardened MySQL image intentionally excludes MySQL Shell. Administrative mysqlsh workflows must
  use a separately scanned tooling image instead of expanding the database server runtime.
