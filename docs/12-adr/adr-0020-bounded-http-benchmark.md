# ADR-0020: Dependency-Free Bounded HTTP Benchmark

> Status: Proposed | Scope: v0.9 Performance baseline slice

## Context

The performance baseline must be portable, auditable, and safe to run in
restricted CI environments without adding production runtime dependencies.

## Decision

Use Python 3.12 standard-library `urllib`, a bounded `ThreadPoolExecutor`,
explicit timeout and response-size limits, no automatic redirects, and
private-network opt-in. Emit only aggregate JSON evidence with optional P99
threshold evaluation.

## Consequences

The harness is easy to reproduce and has a small supply-chain surface. It is not
a replacement for a production load generator or distributed failure test.

## Acceptance

This ADR remains Proposed until an authorized architecture owner accepts it.
