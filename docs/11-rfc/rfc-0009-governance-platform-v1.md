# RFC-0009: Enterprise Governance Platform v1

## Status

Proposed

## Abstract

Define the cross-cutting governance contracts for v0.7: tenant context, audit integrity, model and Prompt
lifecycle, usage and cost accounting, and trace propagation. The proposal makes governance metadata a
first-class platform contract while preserving the v0.1-v0.6 API and SPI surface.

## Motivation

OpenEIP now has durable Agent, Workflow, Knowledge, Connector, and Chat capabilities, but governance
metadata is fragmented. Enterprise deployments need an authoritative tenant boundary, durable audit
evidence, controlled model and Prompt publication, attributable usage and cost, and trace continuity across
Java, Python, asynchronous events, and external providers.

## Scope

Included: tenant and organization context; membership and quota policy; audit event ingestion and query;
model/provider registry and policy; Prompt versioning and publication; token and cost ledger; budgets and
alerts; trace and correlation metadata; administrative REST APIs and Frontend surfaces.

Excluded: Marketplace distribution, Connector/Agent marketplace, Kubernetes and high availability,
SSO/LDAP, and LTS hardening. Secret values, full Prompts, private reasoning, raw Tool arguments, and raw
provider payloads are never persisted as governance data.

## Proposed design

### Ownership

| Area | Owner | Contract |
|---|---|---|
| Tenant, organization, membership, quota | Java control plane | REST, MySQL, request context |
| Audit event authority and query | Java control plane | Transactional outbox, append-only audit records |
| Model and Prompt registry | Java control plane | Versioned REST resources, review and publication |
| Usage and cost ledger | Java control plane | Idempotent usage records and budget decisions |
| Runtime usage emission | Python and Java runtimes | Sanitized internal events with trace context |
| Trace propagation | Gateway, Java, Python, events | W3C trace context and stable request IDs |
| Management UI | Frontend | Public Java OpenAPI only |

Java remains the authorization and durable-state authority. Runtime services receive a validated,
tenant-bound context and may not widen it. Every repository query, event, cache key, and external call must
carry the tenant identifier or an explicitly documented system scope.

### Tenant context

The authenticated request context contains tenant ID, principal ID, organization membership, roles, policy
version, request ID, and trace ID. Tenant context is derived from trusted authentication claims and server-side
membership, never from an untrusted body or query parameter. Cross-tenant access returns the stable not-found
boundary where resource enumeration must be prevented.

### Audit and governance events

Audit records are append-only business evidence with event ID, tenant ID, principal, action, resource type
and ID, outcome, request/trace IDs, policy version, timestamp, schema version, and a hash-chain reference.
Payloads are allowlisted summaries. Writes are idempotent, outbox-backed, and queryable by tenant. Retention,
export, and deletion follow legal policy without exposing secrets or private reasoning.

### Model and Prompt lifecycle

Models and Prompts use immutable versions. A draft can be reviewed, evaluated, published, deprecated, or
rolled back by creating a new published reference; history is never mutated. Providers are represented by
secret references and policy metadata, not credentials. Agent, Chat, and Workflow executions pin model and
Prompt versions and record only safe IDs, digests, usage, and outcomes.

### Usage, cost, and trace

Usage records are idempotent by tenant, execution, provider request, and usage revision. Cost is calculated
from a versioned pricing snapshot and cannot be silently rewritten. Budget checks are authoritative before
new work starts and are rechecked at bounded execution checkpoints. Trace context is propagated across HTTP,
SSE, Java-to-Python calls, Kafka events, and connector calls; spans contain identifiers and bounded attributes,
never Prompt content, chain-of-thought, credentials, or raw Tool values.

## Compatibility and migration

The v1 public APIs and SPIs remain available. Governance APIs are additive under `/api/v2/governance`.
Existing single-tenant behavior uses an explicit system tenant mapping until tenant migration is enabled.
Database changes are additive, use forward migrations with rollback plans, and preserve existing ownership
and authorization tables. Event additions are versioned and tolerant readers are required.

## Alternatives considered

| Alternative | Benefit | Reason rejected |
|---|---|---|
| Tenant ID supplied by clients | Simple integration | Allows spoofing and cross-tenant access |
| Audit only in application logs | Low implementation cost | Not transactional, queryable, or tamper-evident |
| Runtime services own governance state | Local autonomy | Splits authorization and creates inconsistent policy |
| Persist full Prompts and provider payloads | Easy replay | Violates data minimization and privacy boundaries |
| Recompute cost from mutable prices | Simple storage | Historical invoices and budgets become non-reproducible |

## Decision process

This proposal requires public discussion and a Maintainer decision under the RFC process. It remains Proposed
until the discussion window and required vote are complete. Implementation is blocked until RFC-0009 and its
related ADRs are accepted and the independent architecture/security review is approved.

Discussion: Issue #99.

## References

Issue #99; SAD governance boundary; Engineering Principles; ADR-0015; ADR-0016; ADR-0017; ADR-0018.
