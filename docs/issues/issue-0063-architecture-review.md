# Architecture Review: Issue #63 v0.2 Product and Release Polish

> Decision: Approved for implementation | Date: 2026-07-23

| Check | Decision |
|---|---|
| Scope | Limited to defects and usability inside the published v0.1/v0.2 boundary |
| SAD | Java remains the browser-facing authorization and persistence boundary; Python remains internal execution |
| SDD | Recovery resets only FAILED/READY knowledge state and reruns the existing bounded pipeline |
| API | Adds `/processing/retry`; the existing processing command and response schema remain compatible |
| Data | No migration; retry history remains bounded and the current failure code is cleared |
| Runtime | Compose retains MySQL 8.4 LTS, upgrades to digest-pinned 8.4.10, and removes unused administration tooling from the server image |
| Security | OWNER/EDITOR is checked before source access, parsing, or embedding; VIEWER remains read-only |
| UI | Existing Ant Design system is retained; no second component framework or internal API is introduced |
| Compatibility | Additive API and UI behavior; no event, database, SDK, or Agent SPI break |

The READY rebuild command is specific to the v0.2 single-node in-memory vector limitation. Durable
vector reconciliation and background jobs belong to a later architecture review.
