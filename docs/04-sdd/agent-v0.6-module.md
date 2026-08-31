# Agent v0.6 Module Design (Sub-SDD)

> Version: 0.6 | Date: 2026-08-30 | Status: Accepted for implementation
> Issue: #78 | RFC: RFC-0008 | Decisions: ADR-0012, ADR-0013, ADR-0014

## Responsibilities and boundaries

Java `platform-agent` owns tenant-scoped Agent definitions, immutable versions, Tool catalog/grants, MCP
registrations, Memory policy/metadata, Evaluation definitions, durable execution, commands, and safe events.
Python `engine-core` owns bounded planning, execution, reflection, Tool dispatch, Memory projection,
Supervisor/Worker scheduling, and evaluation workers. The MCP gateway alone resolves MCP credentials or
opens remote MCP connections. The browser calls only Java.

Python cannot decide authorization, persist authoritative state, resolve secrets, or create grants. Plugins
cannot directly access network, files, processes, databases, or credentials. Chain-of-thought and provider
prompts are neither API fields nor persisted observability data.

## Aggregates

| Aggregate                       | Identity/version                                                 | Owner          |
| ------------------------------- | ---------------------------------------------------------------- | -------------- |
| AgentDefinition / AgentVersion  | UUID; immutable candidate digest; published integer version      | Java           |
| ToolDefinition / ToolVersion    | reverse-DNS ID; immutable SemVer and digest                      | Java           |
| ToolGrant                       | Agent version + Tool version + resource/argument/approval policy | Java           |
| AgentRun / Step / Attempt       | UUID, revision, pinned dependency snapshot                       | Java           |
| MemoryEntry / MemoryPolicy      | opaque UUID, purpose and retention version                       | Java metadata  |
| McpServer / McpCapability       | UUID; immutable registration/capability versions                 | Java + gateway |
| EvaluationDataset / Suite / Run | UUID; immutable case/scorer versions                             | Java           |

Published objects are immutable. Archive prevents selection for new runs but preserves historical pins. Every
command carries `Idempotency-Key`; mutations use expected revision/ETag.

Before Evaluation, Java freezes the current draft into an immutable `CANDIDATE` snapshot with its draft
revision and digest. Evaluation pins that snapshot and a published baseline. Publish promotes the same row to
`PUBLISHED` only when the run passed and the definition still has the same draft revision and digest. Candidate
rows do not receive or consume an integer version until promotion; runs and Tool grants accept only published
versions. This lifecycle supports first publication without evaluating mutable state or creating version gaps.

## Execution protocol

1. Java authenticates and authorizes, resolves grants, and creates a run with pinned dependencies/budgets.
2. Java issues a signed short-lived capability with IDs, digests, scopes, budgets, and expiry, never secrets.
3. Python claims a leased attempt; its fencing token is required on state/checkpoint commits.
4. Planner writes a public structured plan. Executor uses governed Tool/Worker dispatch. Reflection records an
   outcome class and optional bounded plan patch, never private reasoning.
5. Java atomically commits transitions/outbox events. The UI consumes the ordered timeline.
6. Resume uses committed sanitized references. Cancellation blocks new work and propagates to descendants.

States are `QUEUED`, `PLANNING`, `EXECUTING`, `REFLECTING`, `PAUSED`, `SUCCEEDED`, `FAILED`, and
`CANCELLED`. Terminal states never reopen. Resume is valid only from `PAUSED`; retry creates a new attempt;
cancel is idempotent. Automatic retry requires declared idempotency and no irreversible output.

Default hard limits are 64 steps, 8 plan revisions, depth 4, fan-out 8, 16 active Workers, 128 Tool calls,
3 attempts per retryable step, and 30 minutes wall time, plus tenant token/cost ceilings. Policy may lower
limits. A running budget cannot be raised.

## Tool and permission model

Registration validates immutable JSON schemas, errors, classification, idempotency, side effects, timeout,
result size, cancellation, and secret requirements. Selection is the intersection of the published grant,
caller RBAC/ABAC, resource authorization, execution capability, and runtime policy. Model output and MCP
discovery grant no authority.

`READ` Tools may run automatically if granted. `WRITE` Tools require explicit policy. `DESTRUCTIVE` Tools
always require expiring per-call approval over sanitized arguments. Connector Tools pin Connector type/version
and resource selectors. Workflow Tools start only published versions and propagate correlation/idempotency.

## Memory model

Working Memory is run-local. Session Memory contains user-visible messages/sanitized summaries and has an
absolute TTL. Long-term entries require purpose, provenance, sensitivity, retention, and write policy.
Retrieval returns entry IDs and confidence and never silently edits entries. Delete tombstones synchronously
and exposes purge progress. Derived entries retain lineage for transitive deletion.

Tenant identity is fixed before any Redis key, SQL query, Milvus partition, cache, metric, or log lookup. A
tenant value supplied by a model, Tool, MCP Server, or request body is ignored or rejected.

## Multi-Agent model

Only a published Supervisor delegates. Workers are pinned Agent versions with a capability subset and bounded
context. Handoff records sender, receiver, objective, sanitized references, output schema, deadline, and
capability delta; the receiver accepts before work. Arbitrary calls/cycles are rejected. Runtime-owned bounded
loops count against budgets. Worker results are schema-validated untrusted input. Cancellation propagates.

## MCP boundary

The Client supports managed stdio and registered Streamable HTTP; the Server supports Streamable HTTP. The
official SDK lifecycle is mandatory. Registration, discovery, approval, Tool mapping, and invocation are
separate states. Schema/digest drift suspends mappings. The gateway enforces endpoint/egress policy, DNS
pinning, redirects, TLS, auth references, limits, deadlines, cancellation, and tenant-keyed sessions. Server
discovery lists only explicitly published Tools and invocation repeats authorization.

## Evaluation and release gates

Suites pin datasets, cases, scorers, Agent/Tool versions, fixtures, Memory snapshots, MCP fixtures, and model
configuration. Deterministic suites cover state, authorization, schemas, limits, cancellation, and injection.
Quality suites record scorer provenance. Regression gates define direction, threshold, maximum regression,
sample size, confidence, and flake budget. Cross-tenant access, secret exposure, unsafe side effects, or an
unbounded loop is an unconditional failure.

## Observability and compatibility

Events/traces include safe IDs, version/digest, state, duration, counts, outcome, and redacted summaries. They
exclude prompts, thoughts, secrets, raw Tool values, Memory content, and MCP auth. The v1 endpoint remains; a
compatibility adapter keeps v1 ephemeral, non-delegating, local-MCP-only, and allowlist-based.
