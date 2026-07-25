# 08-connector (Connector Design)

v0.5 covers the complete connector catalog below, not only the seven families summarized in the
roadmap. The common lifecycle, tenant boundary and secret reference rules are defined in
[Connector Module SDD](../04-sdd/connector-module.md),
[RFC-0007](../02-rfc/RFC-0007-connector-control-plane.md), and
[ADR-0011](../05-adr/ADR-0011-connector-secret-boundary.md).
The per-adapter release boundary is the [acceptance matrix](acceptance-matrix.md).

## Release Scope

- [x] Connector control plane and lifecycle API
- [x] MySQL Connector
- [x] PostgreSQL Connector
- [x] Oracle Connector
- [x] SAP Connector
- [x] Redis Connector
- [x] Kafka Connector
- [x] GitHub Connector
- [x] GitLab Connector
- [x] 飞书 Connector
- [x] 企业微信 Connector
- [x] Jira Connector
- [x] Confluence Connector
- [x] MinIO Connector
- [x] OSS Connector
- [x] Email Connector
- [x] Webhook Connector
- [x] Secret provider integration

Checking a connector requires configuration schema, credential isolation, connection test, metadata
extraction, a real read operation, an applicable write/send operation, normalized errors, frontend
configuration and contract tests. Registration in an enum does not satisfy this checklist.

## Verification Notes

The Java quality gate is 80% instruction coverage. Connector transport branches that require a
live SMTP, Kafka, Redis, vendor SaaS or token service are excluded from the unit denominator only
when the adapter has deterministic protocol coverage and is included in release smoke. JDBC H2,
GitHub HTTP, S3-compatible HTTP, Webhook HTTP, Runtime, API and secret-boundary behavior remain in
the denominator and are covered by tests. Live credential validation remains a deployment gate.
