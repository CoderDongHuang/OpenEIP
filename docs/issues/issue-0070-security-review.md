# Security Review: Issue #70 v0.4 Workflow

> Status: Passed for implementation PR | Last updated: 2026-07-24

| Threat | Control | Verification | Status |
|---|---|---|---|
| Cross-tenant definition or execution access | Every repository query and command is tenant-scoped; Java remains the public authorization boundary | Controller/service authorization tests and public-gateway smoke | Passed |
| Malicious or ambiguous workflow graph | Strict JSON fields, node/edge/port allowlists, reachability/cycle checks and bounded control-node configuration | Graph compiler and API validation tests | Passed |
| Webhook secret disclosure or brute force | Secret is shown once, stored only as SHA-256, compared by digest and protected by a stable fixed-window rate limit | Secret persistence and `WF-A-002` rate-limit tests | Passed |
| Trigger replay or duplicate event | Manual/webhook/event commands use idempotency keys; inbound Kafka records persist event ID and payload fingerprint | Duplicate trigger and processed-event tests | Passed |
| Unauthorized or repeated approval | Explicit assignee checks plus durable per-assignee decisions; aggregate transition is conditional and idempotent | ANY/ALL, reject, duplicate and unauthorized-decision tests | Passed |
| Event injection or sensitive payload exposure | Strict v1 JSON Schemas, bounded payloads, explicit SSE DTOs and sanitized outbound projections | OpenAPI/schema tests, after-commit SSE tests and redacted DLQ tests | Passed |
| Poison Kafka event or retry amplification | Dedicated listener factory limits handling to three attempts before a redacted DLQ record | Listener configuration and rejection contract tests | Passed |
| Unbounded input, delay, loop or retry | 64 KiB/100-field execution input cap and bounded delay, loop, retry, event and output settings | Boundary and invalid-configuration tests | Passed |
| Vulnerable dependency, misconfiguration or embedded secret | npm/pip audits and Trivy vulnerability, misconfiguration and secret scanners | Frontend/pip high gates clean; Trivy 656-file snapshot clean | Passed |

## Residual Risk

- v0.4 alpha is a single-node scheduler. It does not claim distributed lease recovery or multi-node
  failover; horizontal execution requires a future claim-owner and lease-expiry design.
- External node calls are at least once around ambiguous failures. Stable invocation IDs permit
  downstream deduplication, but OpenEIP cannot guarantee exactly-once third-party side effects.
- Webhook rate limiting is process-local and appropriate for the alpha single-node profile. A shared
  limiter is required before horizontally scaled ingress.
- Kafka broker outage duration, multi-node consumer rebalancing and production provider capacity are
  not established by the deterministic local benchmark.
