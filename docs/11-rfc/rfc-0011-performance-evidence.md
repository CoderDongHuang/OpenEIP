# RFC-0011: Reproducible Performance Evidence

> Status: Proposed | Scope: v0.9 Performance baseline slice

## Problem

Performance claims require bounded, machine-readable evidence that can be
reproduced without adding runtime dependencies or accidentally targeting a
private production address.

## Proposal

Maintain a standard-library HTTP GET harness with explicit target URL,
bounded requests/concurrency/timeouts/body reads, disabled redirects, private
network opt-in, latency percentiles, throughput, status codes, error classes,
and an optional P99 gate. The result records environment and commit metadata but
never response content or credentials.

## Boundary

The harness is regression evidence only. It does not establish production
capacity, HA, autoscaling, chaos resilience, or multi-node recovery.

## Decision Required

An authorized maintainer must accept or reject this RFC before OEP Step 2 is
complete. The current implementation is explicitly recorded as preceding RFC
acceptance.
