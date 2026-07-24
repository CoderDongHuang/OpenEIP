# ADR-0010: MySQL Durable State Machine and Transactional Outbox

> Status: Accepted | Date: 2026-07-24 | Issue: #70

## Context

Workflow execution must survive process restarts, suspend for approvals and delays, avoid duplicate
logical executions, and integrate with Kafka without claiming exactly-once delivery. Definition state,
execution state, and emitted events must not diverge across separate stores.

## Decision

Use MySQL as the authoritative Workflow store. Persist each execution and node-attempt transition in a
short Spring transaction guarded by an optimistic `lock_version`. Insert the corresponding ordered
execution event and outbox message in that same transaction. A bounded single-node poller resumes due
executions; horizontal claims and leases are deferred beyond this alpha.

External node calls carry a stable `invocationId` derived from execution, node, and logical iteration.
After an ambiguous timeout the engine may invoke again with the same ID, so adapters must be idempotent
or classify the node as not safely retryable. OpenEIP guarantees no rescheduling after a committed
completion, not exactly-once side effects across an uncooperative remote system.

## Consequences

### Positive

- Definition, execution, approval, event history, and outbox transitions share one transaction boundary.
- Restart recovery does not require replaying an unbounded event log.
- Kafka remains optional for local execution while published integration events stay durable.

### Negative

- Polling and due-work indexes add database read/write load.
- External side effects require idempotency cooperation and honest retryability metadata.
- Single-region MySQL remains a v0.4 alpha availability limit.

### Risks

- Accidental concurrent workers: optimistic versions and unique logical keys reject stale commits;
  multi-node side-effect execution is unsupported until explicit leases are added.
- Outbox growth: terminal rows have retention limits and deletion occurs only after delivery evidence.
- Approval races: one conditional state update wins; later decisions receive a stable conflict.

## Alternatives Considered

| Alternative | Benefit | Cost | Reason not selected |
|---|---|---|---|
| Kafka event sourcing | Complete replay log | Complex projections and schema evolution | Excessive for the alpha control plane |
| Redis queues plus MySQL | Fast claims | Cross-store consistency gap | Violates durable transition boundary |
| In-memory executor | Simple | Loses delays and approvals on restart | Fails the product requirement |
