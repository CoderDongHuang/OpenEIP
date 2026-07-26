# Architecture Review: Issue #78 v0.6 Agent

> Review packet status: Complete  
> Decision: Ready for independent review; implementation remains blocked  
> Date: 2026-07-26

## Scope reviewed

RFC-0008, ADR-0012/0013/0014, Agent v0.6 SDD, Agent SPI v2, Tool SPI v1, first-party Agent contracts,
Agent v2 OpenAPI, V2.6 database design, Agent events v2, and Agent Platform UI.

## OEP architecture checklist

| Check                              | Result                                 | Evidence                                                                    |
| ---------------------------------- | -------------------------------------- | --------------------------------------------------------------------------- |
| Module boundaries match SAD        | Pass                                   | Java authority, Python execution, browser-to-Java, isolated MCP gateway     |
| API style matches SDD              | Pass with contract validation required | `/api/v2`, cursor pages, explicit commands, ETag/idempotency, stable errors |
| Database naming/index/migration    | Pass with DDL review required          | additive V2.6 design, tenant-first indexes, revisions, immutable versions   |
| Plugin/Connector rules follow SPI  | Pass                                   | exact versions/digests, runtime dispatch, Connector/Workflow adapters       |
| Events follow Kafka conventions    | Pass                                   | v2 envelope, transactional outbox, safe payload, dedupe and DLQ             |
| Permission model follows RBAC/ABAC | Pass                                   | authorization intersection and short-lived narrowed capability              |
| New dependencies have ADR coverage | Pass for design                        | MySQL/Redis/Milvus reused; MCP gateway and trust decision in ADR-0014       |
| Compatibility is explicit          | Pass                                   | v1 endpoint/SPI unchanged; compatibility registration does not widen grants |

## Cross-module invariants

1. `tenant_id` comes only from authenticated context and precedes every lookup, cache key, vector partition,
   session, event, trace, and metric dimension.
2. Published versions and run dependency snapshots are immutable and digest-pinned.
3. Model proposals, Agent plugins, discovered MCP capabilities, and Memory content never grant authority.
4. MySQL is the durable transition authority; Redis and Milvus are projections with reconciliation/deletion.
5. Tool side effects use idempotency and approval policy; destructive operations are never automatic retries.
6. Public timeline and checkpoints contain sanitized structured state, not private reasoning or secrets.
7. Cancellation, deadlines, and hard budgets propagate to Tool, MCP, Worker, and evaluation operations.

## Review findings

No unresolved design-level architecture contradiction was found. These implementation-entry conditions remain:

- RFC-0008 must complete the one-week discussion and two-thirds Maintainer decision.
- ADR-0012, ADR-0013, and ADR-0014 must be accepted by the architecture decision owner.
- The concrete Flyway DDL must prove tenant-first unique/index/foreign-key behavior and migration rollback plan.
- Generated server/client models must validate the OpenAPI source without weakening schemas.
- Capability signing/verification, lease fencing, and event ordering need executable contract tests.
- Redis/Milvus tenant partition and purge reconciliation need adversarial integration tests.
- MCP gateway deployment/egress policy and official SDK version need a reproducible Streamable HTTP spike.
- An independent reviewer must record approval; this author review is not that approval.

## Decision rationale

The design is internally coherent and follows existing durable Workflow and knowledge storage patterns. It is
ready for governance discussion and independent review, but it is not approved for OEP step 7. Issue #78 must
continue to show implementation blocked until every condition above has evidence.
