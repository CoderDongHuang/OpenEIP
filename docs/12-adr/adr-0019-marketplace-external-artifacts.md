# ADR-0019: Marketplace Metadata and External Artifact Storage

> Status: Proposed | Scope: v0.8 Marketplace

## Context

Marketplace needs immutable version metadata while artifact bytes and registry
availability belong outside the OpenEIP control plane.

## Decision

Persist only tenant-scoped package/version metadata, external URI, SHA-256,
runtime, and manifest. Require lowercase SHA-256 and SemVer validation. Keep
download authorization and signature verification in the external registry
integration. Use composite tenant-leading database keys and optimistic
concurrency for lifecycle commands.

## Consequences

The control plane remains small and auditable, but publication depends on an
external registry contract. Future changes to artifact trust or storage require
a new decision and API compatibility review.

## Acceptance

This ADR remains Proposed until an authorized architecture owner records an
acceptance decision. Its existence does not retroactively satisfy the pre-code
OEP gate.
