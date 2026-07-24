# Workflow Persistence Schema

> Version: v1 | Issue: #70 | Owner: `platform-workflow`

## Tables

| Table | Purpose | Key constraints |
|---|---|---|
| `workflow_definitions` | Draft metadata and published pointer | unique tenant/name among active rows; optimistic `lock_version` |
| `workflow_members` | Workflow RBAC | unique tenant/workflow/user; bounded role |
| `workflow_versions` | Immutable canonical graph | unique tenant/workflow/version and graph SHA-256 |
| `workflow_triggers` | Manual metadata, webhook, cron, Kafka configuration | secret hash only; unique trigger key |
| `workflow_executions` | Durable aggregate and trigger lineage | unique tenant/workflow/idempotency key; optimistic version |
| `workflow_node_executions` | Logical node iterations and numbered attempts | unique execution/node/iteration/attempt; stable invocation ID |
| `workflow_approvals` | Approval task, ANY/ALL mode and aggregate decision | unique execution/node |
| `workflow_approval_decisions` | Single-use assignee decisions | unique approval/assignee and approval/idempotency key |
| `workflow_events` | Ordered sanitized execution history | unique execution/sequence; bounded payload |
| `workflow_outbox` | Transactional Kafka publication | unique event ID; pending/created index |
| `workflow_processed_events` | Inbound Kafka deduplication | unique tenant/event ID plus payload fingerprint |

## Storage Rules

Identifiers are canonical UUID strings. All ownership and lookup indexes begin with `tenant_id`. JSON
columns are validated against application schemas before persistence and are size bounded. Definition
graphs are immutable in `workflow_versions`; executions retain the exact version and digest. Webhook
secrets and credentials are never stored in plaintext.

The v0.4 alpha scheduler is single-node. Due executions are selected from indexed status/resume fields,
and the aggregate optimistic version rejects a concurrent stale commit. A future multi-node scheduler
must add explicit claim-owner and lease-expiry fields before horizontal execution is supported.

## Retention and Rollback

Execution events and outbox rows use explicit terminal timestamps. Delivered outbox rows may be purged
after 7 days; execution history defaults to 30 days while definition/version audit history is retained.
Cleanup is tenant-bounded and batch-limited. The additive `V2.4.0` migration rollback drops foreign
keys and tables in reverse order only when no v0.4 data must be retained.
