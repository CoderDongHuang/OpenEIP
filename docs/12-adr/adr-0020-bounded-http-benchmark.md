# ADR-0020: Dependency-Free Bounded HTTP Benchmark

## Status

Proposed; implementation-ready local decision. Scope: v0.9 Performance baseline slice.

## Date

2026-09-12

## Context

The performance baseline must be portable, auditable, and safe to run in
restricted CI environments without adding production runtime dependencies.
The target is operator supplied and may resolve to an address that must not be
accessed accidentally. Evidence must be useful for regression comparison while
never retaining response content or credentials.

## Decision

Use Python 3.12 standard-library networking with a bounded
`ThreadPoolExecutor`, one DNS resolution per run, pinned TCP/TLS connections,
explicit timeout and response-size limits, no automatic redirects, and
private-network opt-in. Emit only aggregate JSON evidence validated by the v1
result schema, with optional nearest-rank P99 threshold evaluation.

## Consequences

### Positive

- No third-party production dependency or service deployment is required.
- DNS rebinding, redirect escape, oversized responses, and accidental private
  targets have explicit controls.
- The result is reproducible, redacted, and machine-readable.

### Negative

- The harness supports bounded GET measurements only.
- It does not model production capacity, distributed failure, write traffic,
  authentication flows, or provider behavior.
- A local private-network fixture requires an explicit opt-in flag.

### Risks

- A local PASS can be mistaken for a production capacity claim; documentation,
  redacted target metadata, and release gates explicitly prohibit that use.
- The first resolved address may be unreachable when a host has multiple
  addresses; the run records connection failures rather than silently resolving
  again.

## Alternatives Considered

| Option | Advantage | Disadvantage | Decision |
|---|---|---|---|
| Third-party load generator | Rich scenarios and reporting | Runtime/supply-chain cost and out-of-scope topology claims | Rejected |
| Default `urllib` opener | Minimal code | Automatic redirects and DNS resolution are not bounded | Rejected |
| Unpinned per-request DNS | May recover from an unavailable address | Allows DNS rebinding during a run | Rejected |
| Standard-library pinned client | Portable and auditable | Deliberately limited scenario model | Selected |

## Acceptance

The code and local contract tests implement this decision. Architecture-owner
acceptance must still be recorded through the repository review process before
the RFC/ADR can be promoted from Proposed to Accepted.
