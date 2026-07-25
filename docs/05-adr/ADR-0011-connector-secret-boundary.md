# ADR-0011: Keep Connector Credentials Outside the Control Plane

## Status

Accepted for v0.5 alpha.

## Context

Connector configuration is displayed, versioned and validated by the control plane. Persisting
passwords, access tokens or webhook secrets in that JSON would expose them through APIs, logs,
backups and browser state.

## Decision

The API accepts a `credentialRef` using the `secret://` scheme. The database stores only that opaque
reference. Connector adapters resolve it through the platform secret provider at execution time.
Requests containing secret-looking configuration keys are rejected.

## Consequences

The alpha control plane is safe to expose to management clients, but a secret provider and concrete
execution adapters are required before a connector can perform external I/O.
