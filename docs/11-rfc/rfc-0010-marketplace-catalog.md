# RFC-0010: Tenant-Scoped Marketplace Catalog

> Status: Proposed retroactive gap-closure record | Scope: v0.8 Marketplace

## Problem

OpenEIP needs a tenant-scoped catalog for Plugin, Connector, and Agent package
versions without storing artifact bytes in the control plane.

## Proposal

Store immutable package-version metadata, external artifact URI, lowercase
SHA-256, runtime, and manifest metadata. Expose review and publication lifecycle
operations under the Marketplace API. Enforce tenant scope in every query and
use composite tenant-leading keys for database references. Artifact download,
signature verification, and registry availability remain external concerns.

## Security and Compatibility

Management operations require server-derived tenant context, bounded input,
optimistic concurrency, and idempotency keys. The addition is additive to
existing APIs and SPIs. No artifact bytes or credentials are persisted.

## Decision Required

An authorized maintainer must accept or reject this RFC and record the decision
before Marketplace can be represented as having completed OEP Step 2. This
retroactive record does not change the fact that implementation preceded RFC
acceptance.
