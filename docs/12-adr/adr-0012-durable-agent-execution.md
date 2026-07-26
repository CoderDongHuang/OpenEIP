# ADR-0012: Durable Agent Execution with MySQL State and Leased Workers

## Status

Proposed

## Date

2026-07-26

## Context

v0.6 runs may pause, retry, coordinate Workers, or outlive Python processes. The platform already uses
MySQL state machines and transactional outbox. Agent durability must not create a second source of truth.

## Decision

Java owns run, step, attempt, Handoff, command, checkpoint metadata, and events in MySQL. Transitions use an
expected revision and atomically write outbox events. Python workers claim expiring leases with fencing tokens;
stale workers cannot commit. Commands are idempotent. Checkpoints contain bounded sanitized references, not
prompts, chain-of-thought, credentials, or raw Tool payloads. Retry creates a new attempt. Cancellation is
durable and propagates. Stored budgets can only be narrowed after start.

## Consequences

### Positive

- Restart-safe execution with one transactional authority and auditable history.
- Revisions, idempotency, and fencing control duplicate delivery and failover.

### Negative

- More writes and a lease coordinator are required.
- Side effects need adapter idempotency or explicit non-retry policy.

### Risks

- A Tool can finish before checkpoint commit; side-effecting Tools require idempotency and reconciliation.
- Only references and bounded summaries may enter checkpoints.

## Alternatives Considered

| Option                   | Benefit         | Why not selected                       |
| ------------------------ | --------------- | -------------------------------------- |
| Redis source of truth    | Low latency     | Weaker transaction and audit semantics |
| Python-local checkpoints | Simple          | Cannot survive failover                |
| New workflow engine      | Rich primitives | Premature operational lock-in          |
