# RFC-0007: Connector Control Plane

## Decision

Connectors are tenant-scoped resources managed by Java. A connector has a stable type, lifecycle
status, non-secret JSON configuration, and an optional reference to a platform secret. The control
plane owns authorization, validation, persistence, audit-ready timestamps and lifecycle transitions.
Execution adapters are loaded behind the existing versioned `ConnectorSpi` and may not read raw
credentials from API payloads.

## Supported alpha types

`MYSQL`, `POSTGRESQL`, `ORACLE`, `SAP`, `REDIS`, `KAFKA`, `GITHUB`, `GITLAB`, `FEISHU`,
`WECOM`, `JIRA`, `CONFLUENCE`, `MINIO`, `OSS`, `EMAIL`, and `WEBHOOK`.

## Security invariants

1. Every lookup includes the fixed tenant scope and owner authorization.
2. Configurations containing keys matching `password`, `secret`, or `token` are rejected.
3. Credentials are represented only by a `secret://` reference. Secret resolution belongs to the
   platform secret provider, not this module or the browser.
4. Deletion is a soft delete so audit and event consumers can retain the resource identity.

## Lifecycle

New connectors start `PAUSED`. Users may transition to `ACTIVE` or back to `PAUSED`; `ERROR` is
reserved for a health-check adapter. A future execution adapter must record health timestamps and a
bounded error message without mutating the configuration.
