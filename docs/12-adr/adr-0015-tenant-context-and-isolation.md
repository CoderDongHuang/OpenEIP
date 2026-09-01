# ADR-0015: Server-Derived Tenant Context and Default Isolation

## Status

Proposed

## Date

2026-08-31

## Context

Governance resources span Java, Python, Redis, MySQL, search, events, and external calls. A client-provided
tenant identifier is not an authorization boundary and can enable enumeration or cross-tenant access.

## Decision

Derive tenant context from trusted authentication and server-side membership. Carry tenant ID, principal ID,
roles, policy version, request ID, and trace ID through an immutable request context. Require tenant scope on
all repository operations, cache keys, events, and runtime capabilities. Reject missing or mismatched context
by default; use an explicit audited system scope only for migrations and platform administration.

## Consequences

- Tenant isolation is enforced consistently across modules and transports.
- Existing single-tenant flows need an explicit system-tenant mapping during migration.
- Every adapter and test must declare how tenant scope is applied.

## Alternatives considered

| Option | Benefit | Why not selected |
|---|---|---|
| Client-supplied tenant ID | Low friction | Untrusted and spoofable |
| Database-only row filters | Centralized | Does not protect caches, events, or external calls |
| Runtime-local tenant state | Fast local access | Cannot provide one platform-wide authority |
