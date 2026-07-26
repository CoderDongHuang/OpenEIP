# ADR-0014: Isolate MCP Transport, Authentication, and Discovery Behind a Gateway

## Status

Proposed

## Date

2026-07-26

## Context

Remote MCP combines untrusted endpoints, dynamic metadata, credentials, streaming sessions, and execution.
Direct worker access risks SSRF, DNS rebinding, redirect escape, confused credentials, and tenant session reuse.

## Decision

Use the official MCP SDK behind an MCP gateway. Java owns tenant registrations, credential references,
approved endpoint policy, mappings, and lifecycle. The gateway owns stdio/Streamable HTTP sessions, secret
resolution, authentication, DNS/egress policy, limits, cancellation, protocol validation, and safe telemetry.
Registration rejects embedded credentials, private/link-local/metadata ranges, unsafe redirects, ports, and
hosts. DNS is revalidated and connections are pinned. OAuth is audience/scope-bound. Sessions/caches include
tenant, Server version, principal class, auth identity, and capability digest in their keys.

Discovery is quarantined metadata. An administrator maps an approved capability and immutable schemas to a
governed Tool version. OpenEIP's MCP Server authenticates and maps tenant/principal before listing or invoking
explicitly published Tools; the internal catalog is never exposed wholesale.

## Consequences

### Positive

- Network and credential authority are separated from model-driven execution.
- Client and Server modes share policy, audit, limits, and isolation.

### Negative

- The gateway adds an availability and scaling component.
- Unusual network paths require explicit administrator policy.

### Risks

- SDK/protocol changes need pinned compatibility tests.
- Streaming uses byte, message, duration, idle, and concurrency limits.

## Alternatives Considered

| Option                    | Benefit              | Why not selected                            |
| ------------------------- | -------------------- | ------------------------------------------- |
| Direct worker connections | Fewer hops           | Duplicates and weakens egress/secret policy |
| Auto-enable discovery     | Low setup            | Discovery is not authorization              |
| stdio only                | Small attack surface | Does not meet v0.6 scope                    |
