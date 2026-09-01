# Architecture Review: Issue #99 v0.7 Governance

> Review packet status: Ready for independent review
> Decision: Pending independent architecture approval
> Date: 2026-09-01

This packet is prepared against the merged RFC, ADRs, Governance Sub-SDD, and API/Database/UI design. It is
an implementation-entry checklist, not an approval by the design author. A Maintainer or Committer who did not
author the packet must record the final decision before implementation begins.

## Scope reviewed

RFC-0009, ADR-0015/0016/0017/0018, Governance Sub-SDD, Governance OpenAPI v2, V2.7.0 database design,
Governance event schema, and Governance management workspace design.

## Architecture checklist

| Check | Result | Evidence / condition |
|---|---|---|
| Module boundaries match SAD | Pass | Java owns durable governance state; existing modules retain business ownership |
| Tenant context authority | Pass with implementation condition | Context is server-derived and must be enforced before every repository, cache, event, and external call |
| API style and compatibility | Pass | Additive `/api/v2/governance`, cursor pages, ETag/idempotency, stable GOV errors; v1 routes unchanged |
| Database and migration boundary | Pass with DDL condition | Additive `V2.7.0`, tenant-leading indexes, immutable history; concrete DDL and rollback require contract tests |
| Audit consistency | Pass with executable condition | Business mutation, audit row, and outbox row share a transaction; hash-chain verification must be tested |
| Model/Prompt lifecycle | Pass | Review/evaluation precede publication; versions/digests are immutable; rollback creates a new reference |
| Usage and budget authority | Pass with concurrency condition | Java performs idempotent ledger insert and monotonic budget checks under concurrent retries |
| Cross-runtime trace | Pass with propagation condition | W3C trace context and request ID cross HTTP, SSE, Java/Python, Kafka, and Connector boundaries |
| Event contract | Pass after correction | Sanitized event summary has bounded field count and string size; schema validation is mandatory |
| Frontend boundary | Pass | Browser calls Java only; no editable tenant scope and no secret/raw payload rendering |
| Existing SPI/API compatibility | Pass with contract validation | Agent, Workflow, Knowledge, Connector, and Chat contracts remain unchanged; integration tests required |

## Cross-module invariants

1. Tenant scope comes from authenticated server context and is present before SQL, Redis, search, vector,
   Kafka, trace, metric, Connector, and Python calls.
2. Java is the only authority for policy, publication, budget, and audit state. Runtime caches are bounded by
   expiry and policy version and fail closed when stale.
3. Audit, Prompt/model versions, pricing snapshots, usage history, and budget decisions are immutable where
   historical reproducibility requires it.
4. The same idempotency key cannot produce two different outcomes; duplicate delivery is safe and replayable.
5. Governance data is sanitized: no secrets, full Prompts in audit/trace, private reasoning, raw Tool values,
   or provider payloads.
6. v1-v0.6 public APIs and SPIs remain backward compatible, with explicit system-tenant mapping for migration.

## Implementation-entry conditions

- Add concrete `V2.7.0` Flyway DDL and separate rollback SQL, then prove tenant-leading keys, parent/child
  tenant equality, immutable rows, and empty/populated database migration behavior.
- Generate/validate server and client models from the OpenAPI source without weakening `additionalProperties`,
  size, lifecycle, or header requirements.
- Implement tenant context propagation and negative cross-tenant tests at every existing module boundary.
- Prove audit hash-chain canonicalization, transaction/outbox behavior, idempotency conflict handling, and
  at-least-once consumer deduplication.
- Prove model/Prompt review, evaluation, publication, stale-policy, rollback, and execution pin behavior.
- Prove concurrent budget decisions, compensating usage records, pricing reproducibility, and alert idempotency.
- Add contract tests for Java/Python, HTTP/SSE, Kafka, Connector, cache, and trace propagation.

## Decision request

Independent reviewer: record `Approved`, `Approved with Conditions`, or `Rejected` with rationale in this file
and in Issue #99. Until that record exists, OEP step 6 is not complete and implementation remains blocked.
