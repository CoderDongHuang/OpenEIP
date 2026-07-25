# Architecture Review: Issue #71 v0.5 Connector

> Status: Approved for implementation and alpha release preparation | Last updated: 2026-07-25

The 16 adapters share a versioned SPI and a single Java Runtime boundary. Connector configuration
contains only non-secret values; credentials are resolved from `secret://` references at execution
time. Audit operations and inbound Webhook deliveries are tenant-scoped durable records.

- JDBC, Redis, Kafka, HTTP SaaS, S3-compatible storage, Email and Webhook adapters expose the same
  test/metadata/read/write contract, with explicit read-only behavior where applicable.
- The frontend renders each adapter's schema from the catalog, preserving secret fields outside the
  persisted configuration payload.
- Runtime operations enforce active lifecycle state, request size bounds, idempotency keys,
  correlation IDs and normalized errors before invoking an adapter.
- HTTP adapters reject redirects and insecure endpoints by default; `allowInsecure` is explicit for
  local fixtures. Webhook signatures use timestamped HMAC and event IDs are durable and idempotent.

The release smoke remains the deployment boundary. Live provider credentials, private-network
allowlisting and distributed connector execution are intentionally outside the alpha claim.
