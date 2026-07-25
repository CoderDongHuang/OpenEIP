# Issue #71: v0.5 Connector Control Plane

Deliver the v0.5 Connector foundation and all 16 adapters across Java, database and frontend
contracts: MySQL, PostgreSQL, Oracle, SAP, Redis, Kafka, GitHub, GitLab, Feishu, WeCom, Jira,
Confluence, MinIO, OSS, Email and Webhook. Connector implementations must receive credentials by
reference; plaintext secrets are never accepted by the control plane.

## OEP Evidence

| Step | Evidence | State |
|---|---|---|
| 1 Issue | GitHub #71 and v0.5 Connector milestone | Complete |
| 2 RFC | RFC-0007 | Accepted |
| 3 ADR | ADR-0011 | Accepted |
| 4 Module Design | `connector-module.md` | Complete |
| 5 API/DB/UI Design | Connector OpenAPI, migration V2.5.0 and management UI contract | Complete |
| 6 Architecture Review | `issue-0071-architecture-review.md` | Complete |
| 7 Implementation | `platform-connector` control plane and application aggregation | Complete |
| 8 Unit Test | Connector service, Runtime, Webhook and adapter protocol tests | Complete |
| 9 Integration Test | MySQL migration review and authenticated full-stack smoke | Complete |
| 10 Benchmark | Deterministic protocol and catalog acceptance evidence | Complete |
| 11 Security Review | `issue-0071-security-review.md` | Complete |
| 12 Quality Gate | `issue-0071-quality-gate.md` | Complete |
| 13 Docs Update | SDD, RFC/ADR, acceptance matrix, storage, UI and release metadata | Complete |
| 14 Pull Request | PR #75 | Complete |
| 15 Code Review | Independent review and all required CI checks | Complete |
| 16 Merge | PR #75 merged as `41367b4` | Complete |
| 17 Release | `v0.5.0-alpha` only after all gates pass | Pending |

## Initial Scope

- Connector CRUD and lifecycle operations with tenant and owner isolation.
- Type-specific non-secret configuration validation for all 16 v0.5 connector types.
- Credential references (`secret://...`) only; no password, token or secret fields in configuration.
- Every adapter must implement the versioned Connector SPI, connection test, metadata extraction,
  reader, applicable writer/sender, normalized errors and contract tests.
- `v0.5.0-alpha` remains blocked until every adapter and all 17 OEP steps are complete.
