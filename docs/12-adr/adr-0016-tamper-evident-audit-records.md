# ADR-0016: Transactional and Tamper-Evident Audit Records

## Status

Proposed

## Date

2026-08-31

## Context

Enterprise audit evidence must be durable, queryable, tenant-scoped, and correlated with the operation that
caused it. Ordinary logs can be lost, reordered, changed, or contain sensitive payloads.

## Decision

Java owns append-only audit records in MySQL and writes them in the same transaction as the governed state
change. An outbox publishes the sanitized event. Each record contains an immutable event ID, tenant and
principal identifiers, action, resource reference, outcome, request/trace IDs, policy and schema versions,
timestamp, and a hash-chain reference. Payload fields are allowlisted summaries. Writes are idempotent and
raw secrets, Prompts, private reasoning, and raw Tool values are rejected.

## Consequences

- Audit state is consistent with business state and can be replayed through the outbox.
- Hash-chain verification and retention/export policy become operational responsibilities.
- Detailed debugging must use controlled references and traces rather than raw payload capture.

## Alternatives considered

| Option | Benefit | Why not selected |
|---|---|---|
| Application logs only | Minimal schema | No transaction or integrity guarantee |
| Full event payload persistence | Easy diagnosis | Violates data minimization |
| External SIEM as source of truth | Strong operations tooling | Cannot atomically commit with MySQL business state |
