# Governance Module Design (Sub-SDD)

> Version: 0.7 | Date: 2026-09-01 | Status: Accepted for API and persistence design
> Issue: #99 | RFC: RFC-0009 | Decisions: ADR-0015, ADR-0016, ADR-0017, ADR-0018

## 1. Responsibilities and boundaries

Governance is a cross-cutting Java control-plane capability. It is the authority for tenant context,
organization membership, quotas, audit evidence, model/provider policy, Prompt lifecycle, usage and cost
accounting, and governance-facing trace metadata. It provides policy decisions to Agent, Workflow, Knowledge,
Connector, Chat, and the Python engine without taking ownership of those modules' business state.

### 1.1 Owns

- Server-derived tenant and organization context, membership, roles, policy versions, quotas, and budgets.
- Append-only, tenant-scoped audit records and their transactional outbox publication.
- Model registrations, provider policy metadata, pricing snapshots, and secret references.
- Prompt definitions, immutable versions, review/evaluation links, publication references, and rollback history.
- Idempotent usage records, cost attribution, budget decisions, threshold alerts, and reconciliation status.
- Request, execution, and trace correlation contracts at Java boundaries.
- Administrative REST resources and the governance management surfaces in the Frontend.

### 1.2 Does not own

- Authentication key issuance, password storage, or the existing v1 RBAC implementation.
- Agent, Workflow, Connector, Knowledge, Chat, or Python runtime business state.
- Secret values, provider payloads, full Prompt content in audit/trace data, raw Tool arguments, or private
  reasoning.
- Marketplace distribution, SSO/LDAP, Kubernetes/HA operations, or LTS hardening.
- A second authorization authority in Python, Frontend, Kafka consumers, caches, or connectors.

### 1.3 Authority rule

Java resolves and persists governance decisions. A runtime may cache a bounded decision only with an expiry and
policy version. A stale, missing, malformed, or cross-tenant decision fails closed. The Frontend never derives
tenant scope from a form field, URL parameter, or response object; it uses the authenticated server context.

## 2. Deployment and package shape

Governance is a module in the existing Java modular monolith. It does not introduce a new service or database
for v0.7.

```text
com.openeip.governance/
├── api/
│   ├── controller/       # tenant, audit, model, Prompt, usage, trace, quota resources
│   ├── dto/              # versioned request/response contracts; no secret values
│   └── error/            # stable GOV-* error mapping
├── application/
│   ├── context/          # request context derivation and propagation
│   ├── audit/            # transactional append and query policy
│   ├── catalog/          # model/provider and Prompt lifecycle services
│   ├── usage/            # idempotency, pricing, budget and reconciliation
│   ├── quota/            # runtime admission, reservations and lease release
│   └── trace/            # propagation and bounded attribute policy
├── domain/
│   ├── context/          # TenantContext, SystemScope, MembershipPolicy
│   ├── audit/            # AuditRecord, AuditIntegrity, AuditRetention
│   ├── catalog/          # Model, ProviderPolicy, Prompt, Version, Publication
│   ├── usage/            # UsageRecord, PricingSnapshot, BudgetDecision
│   └── trace/            # Correlation, TraceLink, TelemetryPolicy
├── infrastructure/
│   ├── persistence/      # MySQL repositories and Flyway adapters
│   ├── event/            # outbox and Kafka publishers/consumers
│   ├── policy/           # secret-reference and provider policy adapters
│   └── observability/    # tracing bridge and metric exporters
└── shared/               # module errors, identifiers and sanitizers
```

Dependencies point inward. Domain code cannot depend on Spring, Kafka, MySQL, provider SDKs, or Frontend
types. Existing modules integrate through application ports and the shared request-context contract.

## 3. Domain aggregates and invariants

| Aggregate | Owner | Lifecycle / invariants |
|---|---|---|
| Tenant / Organization | Java Governance | Tenant identity is immutable; organization membership is tenant-scoped; suspended tenants reject new work |
| Membership / Quota | Java Governance | Membership changes are revisioned; quota windows and policy versions are explicit |
| QuotaReservation | Java Governance | Admission is idempotent; policy-row locking serializes competing reservations; leases expire |
| AuditRecord | Java Governance | Append-only; idempotent key; hash-chain link; sanitized payload; tenant query only |
| Model / ProviderPolicy | Java Governance | Provider credentials are references; model capabilities and routing policy are versioned |
| Prompt / PromptVersion | Java Governance | Version content is immutable after creation; publication requires review and evaluation evidence |
| Publication | Java Governance | One active published reference per governed name/purpose; rollback creates a new reference |
| UsageRecord / PricingSnapshot | Java Governance | Usage is idempotent; pricing snapshot is immutable; historical cost is not rewritten |
| Budget / BudgetDecision | Java Governance | Start and checkpoint decisions are monotonic; an active execution cannot raise its budget |
| TraceLink / TelemetryPolicy | Java Governance | Correlation IDs are stable; attributes are bounded, allowlisted, and content-free |

All aggregates carry `tenantId` except explicitly audited platform operations. IDs are opaque UUIDs. Commands
require an `Idempotency-Key` and an expected revision or ETag where the resource is mutable. Published and
financial history rows are immutable.

## 4. Tenant context contract

### 4.1 Context fields

```text
TenantContext {
  tenantId: UUID
  organizationId: UUID?
  principalId: UUID
  membershipId: UUID?
  roles: Set<String>
  policyVersion: String
  requestId: String
  traceId: String
  scope: TENANT | SYSTEM
  expiresAt: Instant
}
```

The context is created after authentication and server-side membership lookup. `tenantId` from a request body,
query string, model output, Tool result, MCP response, or client header is ignored for authorization. A trusted
internal caller may supply a correlation header, but Java validates its format and binds it to the authenticated
request.

### 4.2 Propagation rules

- HTTP and SSE: W3C `traceparent` plus `X-Request-Id`; Java derives tenant scope, never accepts tenant scope
  from transport metadata.
- Java-to-Python: signed internal context containing tenant, principal, policy version, request ID, trace ID,
  expiry, and capability digest. It contains no secret or Prompt content.
- Kafka: event envelope carries tenant ID, event ID, request ID, trace ID, schema version, and policy version.
- Redis/cache/search/vector/connector calls: tenant ID is part of the server-built key or selector.
- System scope: only migrations and explicitly named platform administration operations; every use is audited,
  time-limited, and excluded from ordinary tenant queries.

Missing, expired, mismatched, or cross-tenant context returns a stable authorization boundary. Resource lookup
may return not-found when enumeration must be prevented. No downstream component may widen the context.

## 5. Audit model

Audit append is part of the same Java transaction as the governed state mutation. The transaction inserts the
sanitized audit row and an outbox row. Publication failure does not roll back the business mutation, but the
outbox remains retryable until acknowledged. Audit writes are idempotent by `(tenant_id, event_id)` and reject
duplicate event IDs with different content.

```text
AuditRecord {
  eventId, tenantId, principalId, action,
  resourceType, resourceId, outcome,
  requestId, traceId, policyVersion, schemaVersion,
  occurredAt, previousHash, recordHash,
  summary: allowlisted bounded fields
}
```

`recordHash` covers the canonical fields and `previousHash` for the tenant chain. Chain verification reports a
failure without rewriting historical records. Retention and export operate on tenant policy and preserve the
integrity metadata. Audit summaries cannot contain credentials, access tokens, full Prompts, private reasoning,
raw Tool arguments/results, or provider payloads.

## 6. Model and Prompt lifecycle

### 6.1 Model/provider lifecycle

Provider and model registrations use explicit revisions and immutable versions. A provider stores a secret
reference, endpoint policy, allowed capabilities, routing labels, and pricing snapshot references. The secret
resolver remains the only component allowed to resolve the referenced credential.

```text
DRAFT -> REVIEWED -> ENABLED -> DEPRECATED
ENABLED -> SUSPENDED -> ENABLED
```

Suspension blocks new work but preserves historical execution pins. A model selection is valid only when the
tenant policy, Agent/Workflow capability set, provider policy, and budget decision all intersect.

### 6.2 Prompt lifecycle

```text
DRAFT -> IN_REVIEW -> EVALUATED -> PUBLISHED -> DEPRECATED
```

Prompt versions are immutable and identified by version ID and content digest. Review records identify the
review decision and policy version; evaluation records identify the suite/run and threshold result. Rollback
creates a new publication reference to an existing immutable version. Execution records keep IDs and digests,
not Prompt text. Publication is rejected if the evaluation, compatibility, or policy version is stale.

## 7. Usage, cost, quota, and alert model

Runtime and provider adapters emit normalized usage facts. Java verifies the tenant, execution, provider request,
and usage revision before inserting a record. The idempotency key is:

```text
(tenantId, executionId, providerRequestId, usageRevision)
```

Cost is calculated once from an immutable `PricingSnapshot`; the record stores the snapshot ID, units, currency,
rounding mode, and calculated amount. Corrections create compensating records and never mutate a published
historical amount. Budget decisions are made before new work and at bounded checkpoints. Threshold alerts are
idempotent by budget window, threshold, and crossing revision.

Quota exhaustion rejects new work with a stable error and allows only explicitly documented cleanup/cancellation
operations. Retries may resend usage facts safely; they may not bypass a budget decision or increase an active
execution's limit.

### 7.1 Runtime quota enforcement

Runtime callers request admission with a server-selected quota policy, execution ID, bounded token/cost
reservation, one request unit, an optional concurrency unit, an idempotency key, and a lease expiry. Java derives
the tenant and policy version from `TenantContext`; a request cannot select or widen tenant scope.

The application transaction locks the selected `governance_quota_policies` row before reading window usage and
writing a decision. This serializes competing admissions for one policy without introducing a new service or
distributed counter. The observed value for each dimension is:

```text
token       = persisted usage units + active token reservations
cost        = persisted calculated cost + active cost reservations
request     = allowed admissions in the current window
concurrency = allowed, unreleased, unexpired leases
```

`DAILY`, `WEEKLY`, and `MONTHLY` windows use UTC boundaries. `EXECUTION` windows are additionally scoped by
execution ID. A retry with the same idempotency key and identical facts returns the original decision; different
facts return `GOV-I-001`. An exceeded dimension records a denied decision and returns `GOV-B-001`.

Completion or cancellation releases token, cost, and concurrency reservations while retaining the admitted
request in the window count. Expired leases stop contributing to active reservations. A stale policy version,
missing policy, cross-tenant request, malformed lease, or missing transaction fails closed. Runtime integration
must obtain additional admission before an execution consumes more than its active reservation.

## 8. Trace and observability model

Every public request has a stable request ID and a W3C trace context. Child spans use bounded attributes:
tenant ID hash, safe resource IDs, module, operation, policy version, outcome, duration, count, and error code.
Raw Prompt content, chain-of-thought, credentials, raw Tool values, and provider payloads are prohibited.

Trace context must survive HTTP, SSE, Java-to-Python calls, Kafka, and Connector calls. Correlation is tested at
each boundary, including retry and dead-letter paths. Metrics are tenant-safe: high-cardinality raw tenant IDs
are not labels unless an explicit operational policy allows it.

## 9. Persistence and migration boundary

The Governance schema is additive and targets `V2.7.0`. Table names and exact DDL are defined in the following
API/Database design stage, but every table follows the existing baseline:

- binary UUID primary keys, UTC timestamps, optimistic `revision`, and tenant-leading indexes;
- foreign keys and application checks cannot cross tenant boundaries;
- immutable versions and financial/audit history reject update/delete;
- JSON is schema-validated and contains only allowlisted, sanitized data;
- rollback SQL is separate from Flyway and is verified against an empty MySQL database and a populated fixture;
- existing v1-v0.6 tables and APIs are not rewritten; single-tenant deployments use an explicit system-tenant
  mapping during migration.

High-volume audit and usage records may be time-partitioned later without changing logical identity or the
tenant/idempotency keys. Retention cleanup requires an acknowledged outbox state and never deletes integrity
metadata before the applicable policy deadline.

## 10. Integration contracts

Existing modules call Governance ports for context validation, authorization policy, audit append, model/prompt
pinning, budget checks, usage emission, and correlation. Governance does not call module internals or query their
tables directly. Event consumers use tolerant readers and reject an event whose tenant context is absent or
invalid.

The Python engine exposes no public governance authority. It accepts a validated internal context, emits
sanitized usage/trace facts, and treats Java budget or capability decisions as authoritative. Frontend routes
are administrative views over Java REST resources and must not call Python or storage services directly.

## 11. Errors and failure policy

| Code | Meaning | Default behavior |
|---|---|---|
| `GOV-V-001` | Invalid governance request or context | Reject without persistence |
| `GOV-A-001` | Missing, expired, or cross-tenant context | Fail closed; use not-found boundary where required |
| `GOV-A-002` | System scope not permitted | Reject and audit the attempt |
| `GOV-C-001` | Stale revision or ETag | Return conflict; client must re-read |
| `GOV-C-002` | Invalid lifecycle transition | Reject without changing history |
| `GOV-I-001` | Duplicate idempotency key with different payload | Return conflict and preserve original result |
| `GOV-B-001` | Budget or quota exhausted | Reject new work; allow cancellation/cleanup only |
| `GOV-S-001` | Audit/outbox integrity failure | Block affected mutation or quarantine event |

No failure path falls back to a client-supplied tenant, an unversioned model/Prompt, an unbounded budget, or raw
payload logging.

## 12. Design-stage acceptance criteria

The next API/Database/UI design stage must provide:

- OpenAPI contracts for tenant administration, audit query/verification, model/provider policy, Prompt
  lifecycle, usage/cost, budgets/alerts, and trace inspection;
- MySQL ER/DDL and forward/rollback migrations for all authoritative aggregates;
- event schemas for audit, policy, usage, budget, and trace correlation envelopes;
- Frontend information architecture and permission matrix for administrative surfaces;
- compatibility mapping for existing v1 APIs, Agent/Workflow execution pins, Connector calls, and Python
  internal headers;
- contract, isolation, idempotency, lifecycle, and data-minimization test matrices.

Implementation remains blocked until those designs pass an independent architecture and security review.
