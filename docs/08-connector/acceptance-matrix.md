# v0.5 Connector Acceptance Matrix

This matrix is the release boundary for `v0.5.0-alpha`. A connector is complete only when every
required capability is implemented and exercised by a contract test. Vendor credentials and
external infrastructure may be supplied through CI secrets, but deterministic local protocol mocks
must still cover request construction, response parsing, timeouts and error normalization.

| Connector | Test connection | Metadata | Read | Write / send |
|---|---|---|---|---|
| MySQL | JDBC `SELECT 1` | catalogs, schemas, tables, columns | parameterized query / table scan | parameterized DML |
| PostgreSQL | JDBC `SELECT 1` | catalogs, schemas, tables, columns | parameterized query / table scan | parameterized DML |
| Oracle | JDBC `SELECT 1 FROM DUAL` | schemas, tables, columns | parameterized query / table scan | parameterized DML |
| SAP HANA | JDBC `SELECT 1 FROM DUMMY` | schemas, tables, columns | parameterized query / table scan | parameterized DML |
| Redis | RESP `PING` | server info and key types | bounded scan and get | set / delete |
| Kafka | Admin describe cluster | topics and partitions | bounded consumer poll | producer send |
| GitHub | REST authenticated user | repositories and branches | files, commits and issues | create issue / comment |
| GitLab | REST authenticated user | projects and branches | files, commits and issues | create issue / note |
| 飞书 | tenant token verification | drive/wiki spaces | documents and records | message / record write |
| 企业微信 | access-token verification | departments and users | contacts | application message |
| Jira | server info | projects and issue types | JQL issue search | create / update issue |
| Confluence | current user | spaces and content types | CQL content search | create / update page |
| MinIO | S3 service / bucket check | buckets and objects | list / get object | put / delete object |
| OSS | OSS service / bucket check | buckets and objects | list / get object | put / delete object |
| Email | SMTP and IMAP capability check | folders and capabilities | list / read messages | SMTP send |
| Webhook | endpoint validation | delivery contract | received delivery log | signed HTTP delivery |

## Cross-cutting gates

- Config schema is versioned and rendered by the frontend; no connector uses a generic single-field
  form.
- Secrets are resolved from `secret://` references at execution time and are redacted from API
  responses, logs, events and errors.
- Network calls enforce connection and request timeouts, bounded payloads and redirect policy.
- Reads are bounded and writes require an explicit operation allowlist.
- Every operation emits tenant-scoped audit/outbox events with correlation and idempotency keys.
- Health failures use normalized error codes and bounded retry with jitter; authentication failures
  are not retried.
- Integration tests cover tenant isolation, SSRF protection, secret redaction and cancellation.

## Evidence

- `:platform-connector:check` passes Checkstyle, Spotless, SpotBugs, tests and the 80% JaCoCo gate.
- Protocol fixtures cover JDBC/H2, GitHub REST, S3-compatible object storage and signed Webhook
  delivery; Runtime and Webhook inbound tests cover idempotency, bounded requests and failure paths.
- The release smoke test must run against the rebuilt Compose image before tagging `v0.5.0-alpha`.
