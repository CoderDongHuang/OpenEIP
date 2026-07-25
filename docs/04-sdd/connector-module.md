# Connector Module SDD

## Responsibilities

`platform-connector` manages connector metadata, lifecycle and bounded adapter execution. It never
stores credentials. The module exposes `/api/v1/connectors` and `/api/v1/connectors/{id}/...` for
authenticated clients and persists instances, operation audit records and Webhook deliveries using
Flyway migration `V2.5.0`.

## API contract

- `POST /api/v1/connectors`: create a paused connector.
- `GET /api/v1/connectors`: owner-scoped page.
- `GET /api/v1/connectors/{id}` and `PATCH /api/v1/connectors/{id}`: inspect/update metadata.
- `POST /api/v1/connectors/{id}/status`: activate or pause.
- `DELETE /api/v1/connectors/{id}`: soft delete.

The response returns non-secret config and the opaque `credentialRef`; it never returns resolved
credential material.

## Adapter boundary

Adapters implement the versioned SPI from the SAD. They receive non-secret configuration and
resolved credentials through a secret resolver, then report bounded health and execution results.
The Runtime owns active-state checks, request limits, idempotency, audit persistence and normalized
errors. The v0.5 catalog contains 16 adapters spanning JDBC, Redis, Kafka, SaaS HTTP, object storage,
Email and Webhook protocols.

The complete v0.5 catalog contains 16 adapters. Required operations and protocol-level acceptance
tests are defined in `docs/08-connector/acceptance-matrix.md`; a type declaration or configuration
form alone is not an implemented connector.
