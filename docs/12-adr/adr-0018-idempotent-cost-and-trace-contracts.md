# ADR-0018: Idempotent Cost Ledger and Cross-Runtime Trace Context

## Status

Proposed

## Date

2026-08-31

## Context

Usage and cost are emitted by multiple runtimes and providers, while trace context crosses synchronous and
asynchronous boundaries. Duplicate delivery or missing correlation makes budgets and incident analysis
unreliable.

## Decision

Record usage idempotently using tenant, execution, provider request, and usage revision keys. Calculate cost
from an immutable pricing snapshot and retain the snapshot reference. Enforce budget decisions at start and
bounded checkpoints. Propagate W3C trace context plus stable OpenEIP request IDs across HTTP, SSE, Java-to-
Python calls, Kafka, and connector calls. Telemetry attributes are bounded and sanitized.

## Consequences

- Retries do not double-count usage or cost.
- Pricing changes require new snapshots rather than mutating historical amounts.
- All transports and adapters need propagation tests and bounded attribute policies.

## Alternatives considered

| Option | Benefit | Why not selected |
|---|---|---|
| Aggregate counters only | Low storage cost | Loses idempotency and execution attribution |
| Mutable current pricing | Easy calculation | Historical cost is not reproducible |
| Trace only at Gateway | Simple instrumentation | Loses runtime and asynchronous causality |
