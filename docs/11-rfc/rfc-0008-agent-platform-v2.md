# RFC-0008: Production Agent Platform v2

## Status

Proposed

## Abstract

Evolve the bounded v1 Agent runtime into a durable, versioned, tenant-safe platform with Tool,
Memory, Planner, Multi-Agent, MCP, Evaluation, and five first-party Agent types.

## Motivation

The v0.2 runtime proves bounded single-execution Tool calling with ephemeral state and local stdio
MCP. It cannot publish Agent definitions, resume work, govern reusable Tools, retain permitted Memory,
coordinate Workers, connect remote MCP Servers, or prevent quality regression. v0.6 supplies those
capabilities without weakening the v1 rule that model output never grants authority.

## Scope

Included: versioned Agent definitions; Tool SPI/catalog/grants and Connector/Workflow Tools; governed
session and long-term Memory; durable Plan/Execute/Reflection; Supervisor/Worker/Handoff; stdio and
Streamable HTTP MCP Client/Server; versioned Evaluation; and Document, SQL, BI, Search, and Workflow
Agents. Excluded: v0.8 Marketplace distribution, candidate Agent types, and v0.7 general governance.
Chain-of-thought, provider prompts, raw credentials, and unrestricted Tool output are never persisted.

## Design

### Ownership

| Area                                                   | Owner                    | Contract                                |
| ------------------------------------------------------ | ------------------------ | --------------------------------------- |
| Definitions, versions, grants, lifecycle, durable runs | Java control plane       | REST, MySQL, Outbox                     |
| Planning, execution, reflection, Tool/MCP calls        | Python execution plane   | Internal HTTP/SSE                       |
| Session Memory                                         | Memory service           | Redis, tenant-prefixed keys             |
| Long-term Memory                                       | Knowledge/Memory adapter | MySQL metadata plus Milvus partitions   |
| MCP network access                                     | MCP gateway              | Streamable HTTP, egress and auth policy |
| Management UI                                          | Frontend                 | Public Java OpenAPI only                |

Java is the authorization authority. Python receives a signed, short-lived execution capability that
pins tenant, principal, Agent version, Tool versions, resource scopes, budgets, and expiry. Python may
narrow but never widen it. Every lookup and mutation includes tenant scope.

### Version and Tool model

An Agent definition has one mutable draft. Publishing creates an immutable monotonic version and content
digest. A run pins Agent, Tool, MCP capability, model/prompt policy, Memory policy, and evaluation versions.
Rollback copies an old version into a new draft and never mutates history.

Tool SPI v1 separates metadata/schema from invocation. Immutable Tool versions have a risk class (`READ`,
`WRITE`, `DESTRUCTIVE`, `EXTERNAL`). A grant binds an Agent version to a Tool version, operations, resource
selectors, argument constraints, approval mode, and expiry. Connector and Workflow adapters expose existing
contracts as Tools; credentials remain references. Both the grant and execution capability are required.

### Memory

Working Memory remains local. Session Memory stores bounded sanitized messages/summaries under an absolute
TTL. Long-term entries record purpose, source, consent basis, sensitivity, retention deadline, lineage, and
embedding version. MySQL owns metadata/deletion state; Milvus stores tenant-partitioned vectors and opaque
IDs. Retrieval filters by tenant, principal, Agent, purpose, sensitivity, and expiry. Deletion tombstones
immediately, emits an idempotent purge, removes vector/cache/derived copies, and records completion.

### Durable execution and Multi-Agent

The run state machine is `QUEUED -> PLANNING -> EXECUTING <-> REFLECTING`, with `PAUSED`, `SUCCEEDED`,
`FAILED`, and `CANCELLED` paths. State changes and events commit together. Leased workers use fencing tokens;
commands use idempotency keys and expected revisions. Resume uses sanitized checkpoints. Reflection emits a
decision and revised public plan, never private reasoning.

A Supervisor starts pinned Worker versions and explicit Handoffs. The runtime enforces hard limits for steps,
revisions, depth, fan-out, active Workers, Tool calls, repeated edges, retries, time, tokens, and cost. Workers
receive least-privilege capability subsets. Cancellation propagates. Arbitrary cycles are rejected.

### MCP

Servers are explicitly registered per tenant. Discovery is quarantined metadata; administrators map selected
capabilities into governed Tool versions. Streamable HTTP requires HTTPS except loopback fixtures, allowlisted
egress, private/link-local/metadata IP rejection, DNS validation/pinning, redirect rejection, bounded payloads,
timeouts, and cancellation. The gateway alone resolves auth references. OpenEIP's MCP Server authenticates
and maps tenant/principal before discovery or invocation and exposes only explicitly published Tools.

### Evaluation

Datasets/cases are immutable versions pinning fixtures, assertions, scorer versions, and resource snapshots.
Runs pin Agent dependencies and environment. Metrics include task success, Tool correctness, policy violations,
quality/groundedness, latency, tokens/cost, and flakes. Candidate versus baseline gates define sample size and
confidence; any deterministic safety failure blocks unconditionally. Grader identity and version are recorded.

## Compatibility and Migration

The v1 endpoint remains during v0.6. Agent SPI v1 runs through a compatibility adapter with ephemeral Memory
and no durable/Multi-Agent claim. Agent SPI v2 and Tool SPI v1 are additive. Deploy additive schema, register
the constrained v1 Agent as a compatibility version, and enable each first-party Agent only after evaluation.
No existing request allowlist becomes a persistent grant without administrator confirmation.

## Impact

- API/SDK: additive `/api/v2` resources and generated clients; v1 remains available.
- Plugin SPI: Agent SPI v2, Tool SPI v1, and a v1 adapter.
- Database: additive v2.6 migration for definitions, versions, grants, runs, Memory, MCP, Evaluation, events.
- Security: capability tokens, tenant filters, retention/deletion, egress isolation, approval, safe audit.

## Alternatives Considered

| Alternative                        | Benefit          | Reason rejected                                 |
| ---------------------------------- | ---------------- | ----------------------------------------------- |
| Framework-specific public contract | Faster prototype | Locks persistence and plugins to one framework  |
| Python owns authorization/state    | Fewer calls      | Splits enterprise transaction authority         |
| Persist full prompts/traces        | Easy replay      | Retains sensitive content and private reasoning |
| Auto-enable discovered MCP Tools   | Simple setup     | Discovery cannot grant authority                |
| Generic builder only               | Smaller release  | Does not prove five PRD workflows               |

## Decision Process

This proposal requires at least one week of public discussion and a two-thirds Maintainer vote. Append the
vote, rationale, and decision date before changing the status to Accepted.

Public discussion opened on 2026-07-26 at
https://github.com/CoderDongHuang/OpenEIP/discussions/80. The earliest valid Maintainer decision date is
2026-08-02.

## References

Issue #78; Discussion #80; PR #79; ADR-0007; ADR-0012; ADR-0013; ADR-0014; Agent v0.6 Module SDD.
