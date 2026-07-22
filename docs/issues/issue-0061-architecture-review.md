# Architecture Review: Issue #61 Operational Frontend Workspace

> Decision: Approved for implementation | Date: 2026-07-22

| Check | Decision |
|---|---|
| RFC | RFC-0004 defines the public workspace and server-side ingestion boundary |
| ADR | ADR-0008 accepts bounded synchronous ingestion for the single-node MVP |
| SAD | Java remains the identity, authorization, persistence, and browser-facing boundary |
| SDD | Feature-oriented React views consume typed same-origin APIs; no internal Python API is public |
| API | Adds one idempotent-by-state processing command; existing APIs remain compatible |
| Security | Token remains session-scoped; content is text-only; destructive actions require confirmation |
| Compatibility | Additive public endpoint and UI; no migration, event, or SPI change |

Coding may begin. Production background processing, cancellation, bulk ingestion, retry policy,
provider selection, and progress percentages require a later architecture review.
