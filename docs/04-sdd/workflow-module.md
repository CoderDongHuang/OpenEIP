# Workflow Module Design (Sub-SDD)

> Version: 1.0 | Date: 2026-07-24 | Status: Approved for implementation
> Issue: [#70](https://github.com/CoderDongHuang/OpenEIP/issues/70) | RFC: [RFC-0006](../11-rfc/rfc-0006-durable-workflow-orchestration.md) | ADR: [ADR-0010](../12-adr/adr-0010-durable-state-machine-outbox.md)

## 1. Ownership and Boundaries

`platform-workflow` is the Java control-plane owner for Workflow definitions, versions, membership,
triggers, executions, node attempts, approvals, ordered events, retry, and outbox delivery. It exposes
public APIs only after JWT and tenant authorization. Python and existing Agent/Knowledge capabilities
are invoked through bounded adapter ports; they do not own Workflow state.

The frontend Canvas edits a draft representation and displays server events. It never decides whether a
graph is valid, an approval is authorized, or an execution may retry.

## 2. Definition and Version Model

A workflow has mutable draft metadata and a canonical draft graph. Publish validates and canonicalizes
the graph, stores an immutable numbered version and SHA-256 digest, and points the workflow at that
version. Restore copies an old version into a new draft; history is never overwritten.

Limits for v0.4 alpha are 100 nodes, 200 edges, fan-out 10, loop iterations 100, node output 64 KiB,
execution events 10,000, total runtime 24 hours, node timeout 30 minutes, and retry attempts 5. Start and
End are required. Arbitrary cycles, recursion, unknown node types, duplicate IDs, invalid ports, and
unreachable nodes fail before publication.

Built-in node types are Start, End, LLM, Agent, Tool, Condition, Loop, Approval, Delay, and Webhook.
Each has `type`, `schemaVersion`, bounded configuration, declared input/output ports, and retryability.
Secrets are references to server-managed credentials, never graph values or event payloads.

## 3. Access Model

Every aggregate contains `tenant_id`. Membership roles are OWNER, EDITOR, RUNNER, APPROVER, and VIEWER.
OWNER manages membership/delete; EDITOR edits and publishes; RUNNER triggers/cancels/retries; APPROVER
may decide only explicitly assigned approval tasks; VIEWER reads metadata and sanitized history. Admin
may operate but all repository queries remain tenant-scoped. Missing and inaccessible resources share a
not-found error.

## 4. Execution State Machine

```text
QUEUED -> RUNNING -> WAITING_APPROVAL -> RUNNING -> SUCCEEDED
                    WAITING_DELAY    -> RUNNING
                    RETRY_WAIT       -> RUNNING
RUNNING/WAITING_* -> CANCELLING -> CANCELLED
RUNNING/WAITING_* -> FAILED
```

Node attempts move through PENDING, CLAIMED, RUNNING, WAITING, SUCCEEDED, FAILED, SKIPPED, or CANCELLED.
One transaction persists the aggregate transition, ordered event, and outbox row. A lease expiry returns
ambiguous work to a recoverable state with the same invocation ID. Parallel branches join only after all
required predecessors are terminal-success. Condition and Loop select only declared outgoing ports.

## 5. Trigger, Approval, and Retry

- Manual trigger requires membership and an `Idempotency-Key`.
- Webhook trigger uses a random secret shown once and stored only as a hash; request body is size bounded.
- Cron uses a validated five-field UTC expression, computes the next time server-side, and deduplicates
  by trigger plus scheduled instant.
- Kafka accepts only configured event types and versions, validates strict schemas, and deduplicates
  `(tenant_id, event_id)` before creating an execution.
- Approval tasks store explicit assignees, ANY/ALL mode, expiry, and one decision per assignee. Reassign
  is owner/editor-only and never changes an already decided task.
- Automatic retry applies only to declared retryable codes. Delay uses bounded exponential backoff.
  Manual retry creates a new node attempt in the same execution lineage and cannot reopen a terminal
  successful execution.

## 6. Events and Observability

The engine stores ordered sanitized events and streams them over SSE with heartbeat and resume by last
sequence. Public events never contain secrets, prompts, raw webhook bodies, node credentials, or output
above the configured excerpt limit. Kafka schemas cover workflow published, execution started/waiting/
completed/failed, and approval requested/decided. Metrics include queue delay, node duration, retry,
approval age, lease recovery, outbox lag, duplicate triggers, and terminal outcomes.

## 7. Failure and Compatibility

Errors use stable `WF-*` codes. Validation and authorization failures never start an execution. Unknown
events fail closed. Poison Kafka messages retry at most three times before a sanitized DLQ envelope.
All API, database, and event changes are additive to v0.3. Dynamic Workflow plugins, arbitrary code,
HA consensus, and BPMN compatibility are excluded from v0.4 alpha.

## 8. Quality Gates

Unit and integration tests cover graph compilation, all state transitions, restart recovery, concurrency,
authorization, triggers, approvals, retry, outbox, SSE, strict contracts, and MySQL migration rollback.
Coverage remains at least 80%. Benchmarks measure 1,000-node-transition P99, trigger-to-queue latency,
and 100-way parallel execution without external providers.
