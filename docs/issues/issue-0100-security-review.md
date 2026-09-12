# Security Review: v0.9 Performance Baseline

> Local security review: complete for the bounded utility and deterministic
> tests | Independent security approval: required before release

## Threat Review

| Threat | Control | Evidence | Local status |
|---|---|---|---|
| SSRF to private/metadata networks | Resolve all addresses; reject private, loopback, link-local, reserved, multicast unless explicit local opt-in | URL validation tests and address classification | Pass |
| DNS rebinding | Resolve once and pin the selected address for every request | `_pinned_opener` and loopback transport test | Pass |
| Redirect escape | Disable `HTTPRedirectHandler`; pinned transport has no redirect path | Redirect test returns `http_error` | Pass |
| Credential leakage | Reject URL credentials; target evidence stores scheme and host only | URL validation and JSON contract tests | Pass |
| Response data leakage | Never serialize body or headers; only aggregate counts and timings are emitted | Result schema and output contract review | Pass |
| Oversized response memory pressure | Read only through the `maxResponseBytes + 1` sentinel | Bounded-reader and oversized response tests | Pass |
| Slow or stalled target | Per-request timeout and bounded concurrency | Timeout classification test and hard limits | Pass |
| Malformed endpoint | HTTP(S), hostname, port, fragment, and DNS validation before requests | URL and empty-resolution tests | Pass |
| False production claim | Explicit SDD/release boundary excludes HA/capacity/chaos claims | SDD, RFC, ADR, checklist | Pass |

## Scan Boundary

The utility adds no production dependency. Repository, dependency, container,
IaC, secret, and external-target scans remain release-environment checks and
must be attached to the release commit. The local machine used for this change
does not have Docker/Trivy available, so no scan result is claimed here.

## Required Independent Decision

The security reviewer must confirm the threat table, review the repository scan
output, and record approval or findings before the release gate can pass. Local
tests are necessary evidence but are not independent approval.
