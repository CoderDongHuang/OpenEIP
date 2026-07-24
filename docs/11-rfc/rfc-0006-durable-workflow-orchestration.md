# RFC-0006: Durable Workflow Orchestration

> Status: Accepted for implementation | Issue: #70 | Version: v0.4

## Abstract

OpenEIP v0.4 adds a Java-owned, tenant-scoped Workflow control plane and durable execution engine with
an operational React Canvas, versioned definitions, triggers, approvals, retry, and Kafka integration.

## Motivation

Agent streaming executes one bounded request but cannot represent long-running business processes that
pause for approval, wait for time, branch, retry, or resume after a restart. Workflow definitions also
need immutable publication, access control, audit history, and deterministic execution independent of a
browser session.

## Design

`platform-workflow` owns definitions, immutable versions, membership, triggers, executions, node
attempts, approvals, ordered execution events, inbound deduplication, and a transactional outbox. MySQL
is the source of truth. The v0.4 alpha scheduler resumes indexed due work on one node and uses optimistic
versions to reject stale commits; distributed leases are deferred until multi-node execution.
Every node attempt receives a stable invocation ID; retry reuses the logical node input and creates a
new numbered attempt. Completed nodes are never scheduled again.

Definitions are canonical JSON graphs with typed nodes and edges. Publication rejects unknown fields,
unreachable nodes, illegal cycles, invalid ports, excessive fan-out, missing Start/End nodes, and bounds
outside platform policy. Loop is the only cycle-capable construct and is compiled into a bounded engine
instruction rather than stored as an arbitrary graph cycle.

Manual, webhook, cron, and allowlisted Kafka triggers converge on one idempotent execution command.
Approvals suspend the execution and resume it only after an authorized, single-use decision. State
transitions and outbox records commit in the same database transaction. Kafka delivery remains at least
once; consumers deduplicate `(tenant_id, event_id)` and poison events go to a bounded DLQ path.

The React workspace uses `@xyflow/react` for graph interaction. It edits drafts only; the server remains
the validation, authorization, and execution boundary.

## Alternatives Considered

| Alternative | Decision |
|---|---|
| Temporal/Camunda as the v0.4 runtime | Deferred: strong durability, but adds a second persistence and operational model before OpenEIP's node and authorization contracts stabilize. |
| Python/LangGraph as workflow owner | Rejected: approval, versioning, RBAC, triggers, and transaction boundaries belong to the Java control plane. |
| Browser-executed graph | Rejected: cannot provide durable, authorized, restart-safe execution. |
| Arbitrary cyclic graph | Rejected: termination and retry cost cannot be bounded statically. |

## Impact

- API: additive `/api/v1/workflows`, execution, trigger, approval, webhook, and SSE endpoints.
- SDK: no public SDK in v0.4 alpha; OpenAPI remains the source contract.
- Plugin SPI: no dynamic `WorkflowNodeSpi` loading in this release; node contracts are versioned first.
- Database: additive MySQL tables and indexes described in the Workflow schema.
- Security: server-side membership, canonical graph validation, hashed webhook secrets, bounded outputs,
  stable errors, and redacted events are mandatory.

## Migration Plan

The module is additive. Migration creates empty Workflow tables and can be rolled back before use by
dropping those tables in reverse dependency order. Published versions are immutable; rollback creates a
new draft/version and never mutates historical executions.

## References

- GitHub Issue #70
- ADR-0010
- Workflow Module Design
- Workflow API, database, and Canvas design documents
