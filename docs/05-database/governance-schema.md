# Governance Persistence Schema

> Version: 0.7 | Migration target: `V2.7.0` | Status: Accepted for architecture review
> Issue: #99 | ADRs: ADR-0015, ADR-0016, ADR-0017, ADR-0018

## 1. Storage baseline

Governance uses the existing Java/MySQL authority. The migration is additive and does not alter v1-v0.6
business tables. Every tenant-owned table uses a binary UUID primary key, UTC timestamps, optimistic `revision`,
and indexes beginning with `tenant_id`. Platform system-scope rows are explicit, audited, and cannot be returned
by ordinary tenant queries.

JSON fields are schema-validated, size-bounded, and limited to allowlisted summaries or policy metadata. They do
not contain credentials, access tokens, full Prompts in audit/trace rows, private reasoning, raw Tool arguments,
or provider payloads.

## 2. Tables

| Table | Purpose | Key constraints |
|---|---|---|
| `governance_tenants` | Tenant identity, state, policy pointer | immutable ID; unique display slug; `ACTIVE/SUSPENDED/ARCHIVED` |
| `governance_organizations` | Tenant organization metadata | unique `(tenant_id, name)`; tenant FK |
| `governance_memberships` | Principal membership and roles | unique `(tenant_id, principal_id)`; revisioned role set; no cross-tenant FK |
| `governance_quota_policies` | Token, cost, request and concurrency windows | immutable policy versions; tenant-leading window indexes |
| `governance_quota_reservations` | Runtime quota admission decisions and expiring leases | idempotent policy key; append-only decision; release timestamp only |
| `governance_audit_records` | Append-only evidence and hash chain | unique `(tenant_id, event_id)`; immutable hash fields; sanitized summary |
| `governance_outbox` | Transactional audit/policy event publication | unique event ID; retry state; acknowledged retention deadline |
| `governance_providers` | Provider endpoint policy and secret reference | no credential value; tenant-scoped endpoint/capability policy |
| `governance_models` | Model registration and state | unique `(tenant_id, name)`; policy revision; no mutable execution pin |
| `governance_model_versions` | Immutable capability/routing/pricing references | unique `(tenant_id, model_id, version)`; digest required |
| `governance_prompts` | Prompt definition and active publication pointer | unique `(tenant_id, name, purpose)`; revisioned pointer |
| `governance_prompt_versions` | Immutable Prompt content and digest | unique `(tenant_id, prompt_id, version)`; content encrypted at rest |
| `governance_prompt_reviews` | Review and evaluation linkage | reviewer, decision, policy version, immutable evidence reference |
| `governance_prompt_publications` | Publication/rollback history | append-only; version and digest pin; one active reference per Prompt |
| `governance_pricing_snapshots` | Immutable provider/model prices | unique provider/model/version; currency and rounding fixed |
| `governance_usage_records` | Normalized token/unit usage and calculated cost | idempotent tenant/execution/provider request/revision key |
| `governance_budgets` | Budget policy and current window | tenant/name unique; no increase for an active decision |
| `governance_budget_decisions` | Start/checkpoint authorization decisions | append-only; execution and policy version pin |
| `governance_alerts` | Threshold crossing notifications | idempotent budget/window/threshold/crossing revision |
| `governance_trace_links` | Bounded cross-runtime correlation metadata | tenant-scoped trace/request indexes; no content payload |

## 3. Relationships and invariants

```text
tenant
  ├── organization ──< membership
  ├── quota_policy ──< quota_reservation
  ├── audit_record ──< outbox
  ├── provider ──< model ──< model_version ──> pricing_snapshot
  ├── prompt ──< prompt_version ──< prompt_review
  │                  └────< prompt_publication
  ├── budget ──< budget_decision ──< alert
  ├── usage_record ──> pricing_snapshot
  └── trace_link
```

- Every child row includes `tenant_id`; application checks compare parent and child tenant IDs before writes.
- `governance_audit_records`, `governance_prompt_versions`, `governance_prompt_publications`,
  `governance_model_versions`, `governance_pricing_snapshots`, `governance_usage_records`, and
  `governance_budget_decisions` reject update/delete through application policy. Quota reservation decision
  facts are immutable; only the nullable release timestamp may transition once.
- Mutable aggregates require expected `revision`; stale writes return `GOV-C-001` and do not append a second
  business result.
- Tenant suspension rejects new Agent/Workflow/Chat work and new provider calls while allowing audited cleanup
  and cancellation.
- A publication references only an immutable version whose review, evaluation, compatibility, and policy
  versions are still valid.

## 4. Audit record shape

`governance_audit_records` stores:

```text
id, tenant_id, event_id, principal_id, action,
resource_type, resource_id, outcome,
request_id, trace_id, policy_version, schema_version,
occurred_at, previous_hash, record_hash, summary_json
```

`record_hash` is calculated from canonical field order plus `previous_hash`. The unique event ID makes retries
safe; a duplicate event with a different fingerprint is a conflict. The hash chain is per tenant, and system
scope uses a separately named chain. Verification reads a bounded time range and never repairs history.

The audit row and its outbox row are inserted in the same transaction as the governed state mutation. Outbox
delivery is at-least-once and uses event ID deduplication. An undelivered outbox row is retained until delivery
acknowledgement and the audit retention deadline.

## 5. Usage and budget constraints

`governance_usage_records` has a unique key on:

```text
(tenant_id, execution_id, provider_request_id, usage_revision)
```

The row stores units, unit type, pricing snapshot ID, currency, rounding mode, calculated amount, request/trace
IDs, and a safe source reference. Corrections are compensating rows linked to the original record. A pricing
snapshot is never updated after first use.

`governance_budget_decisions` records the decision type (`START` or `CHECKPOINT`), policy version, observed
usage, reserved amount, decision, and execution reference. Decisions are monotonic for an execution. Alerts use
`(tenant_id, budget_id, window_start, threshold, crossing_revision)` as their idempotency key.

`governance_quota_reservations` records one admission attempt with the policy and execution IDs, policy version,
UTC window bounds, requested token/cost/request/concurrency units, decision, request/trace IDs, lease expiry, and
optional release time. Its idempotency key is:

```text
(tenant_id, quota_policy_id, idempotency_key)
```

Allowed request units remain counted after release. Token, cost, and concurrency reservations contribute only
while `released_at IS NULL AND expires_at > now`. The policy row is selected `FOR UPDATE` before aggregate reads
and decision insertion, so competing admissions for one policy cannot oversell the same window.

## 6. Tenant and membership migration

Existing single-tenant installations receive an explicit system tenant mapping through an idempotent application
command. The command does not infer tenant identity from client data and is recorded in the system audit chain.
Existing Auth users are linked to a membership only after server-side validation. No existing v1 endpoint changes
its request shape in `V2.7.0`; Governance APIs are additive under `/api/v2/governance`.

## 7. Index and retention policy

Required index prefixes include:

```text
(tenant_id, state, revision, id)
(tenant_id, occurred_at, id)
(tenant_id, retention_deadline, id)
(tenant_id, execution_id, provider_request_id, usage_revision)
(tenant_id, quota_policy_id, window_start, decision, expires_at)
(tenant_id, quota_policy_id, idempotency_key)
(tenant_id, trace_id, occurred_at, id)
```

Audit, usage, alert, and trace rows may be time-partitioned later without changing logical keys. Retention is
tenant-policy driven, batch-limited, and observable. Deleted Prompt/usage content is tombstoned before purge;
audit integrity metadata and legally retained evidence are not removed by ordinary cleanup.

## 8. Migration and rollback

`V2.7.0__init_governance_schema.sql` creates the tables in dependency order and adds only new indexes and
constraints. A separate `U2.7.0__init_governance_schema.sql` documents reverse-order table removal for disposable
development databases; it is never run by Flyway and cannot be used to erase released tenant evidence.

Contract verification must cover an empty MySQL database, populated fixtures, foreign-key tenant mismatch,
duplicate idempotency keys, hash-chain verification, prompt immutability, pricing reproducibility, budget
boundaries, retention deadlines, and rollback safety. Migration identifiers are never reused after release.

## 9. Design restrictions

No schema may provide a client-controlled tenant column, a plaintext secret column, an audit payload escape hatch,
or a mutable pointer that silently changes a historical execution's model, Prompt, price, policy, or trace link.
