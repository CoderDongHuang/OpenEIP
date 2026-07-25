# Security Review: Issue #71 v0.5 Connector

> Status: Passed for alpha release preparation | Last updated: 2026-07-25

| Threat | Control | Verification | Status |
|---|---|---|---|
| Secret leakage | Only `secret://` references enter CRUD; resolver values never enter DTOs or config JSON | Connector service and resolver tests | Passed |
| Cross-tenant access | Tenant and owner predicates apply to instances, operations and Webhook deliveries | Runtime/service tests and repository contract | Passed |
| SSRF and downgrade | HTTP/S3/Webhook endpoints require HTTPS unless explicit `allowInsecure=true`; redirects disabled | Adapter endpoint tests and protocol fixtures | Passed |
| Webhook forgery/replay | Timestamp window, constant-time HMAC comparison and unique event ID | Inbound signature, expiry and duplicate tests | Passed |
| Unbounded payload or query | 1 MiB Runtime request cap, bounded reads, identifier/path validation and response limits | Runtime/JDBC/object-store tests | Passed |
| SQL or command injection | Identifiers validated against strict patterns and values use prepared parameters | JDBC H2 protocol test and SpotBugs SQL review | Passed |
| Credential-bearing errors | Adapter errors use stable codes and sanitized messages | SpotBugs, runtime normalization and smoke review | Passed |

## Residual Risk

Private-network connectors still require an explicit production allowlist before deployment. Provider
rate limits, broker failover and exactly-once third-party side effects are not claimed by the alpha.
