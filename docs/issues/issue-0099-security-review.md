# Security Design Review: Issue #99 v0.7 Governance

> Review packet status: Approved with Conditions
> Decision: Approved to enter implementation with conditions
> Date: 2026-09-02

This document records the threat model and required implementation evidence for v0.7 Governance. The
independent reviewer `WriteBigBug` recorded `Approved with Conditions` in Issue #99 on 2026-09-02. This is an
implementation-entry approval, not a release security gate.

## Assets and trust boundaries

Assets include tenant identity and membership, policy versions, audit evidence, model/provider metadata, Prompt
versions, pricing and usage records, budget decisions, trace metadata, secret references, and downstream Agent,
Workflow, Connector, Knowledge, and Chat operations. Boundaries are browser-to-Java, Java-to-Python, Java-to-
Kafka, Java-to-storage, governance-to-secret resolver, and governance-to-existing-module adapters.

## Threat review

| Threat | Required control | Evidence before implementation entry/release |
|---|---|---|
| Tenant spoofing/confusion | Server-derived context; tenant-first keys; no client tenant authority | Cross-tenant API, cache, event, search, vector, and adapter negative tests |
| Privilege escalation | Membership/policy version checks; explicit system scope; fail closed | Role matrix, stale policy, system-scope audit, and confused-deputy tests |
| Audit tampering | Same-transaction append/outbox; canonical hash chain; immutable rows | Duplicate/conflicting event, chain-break, replay, retention, and export tests |
| Secret exposure | Secret references only; resolver boundary; no secret in logs/events/UI | Secret canaries across API, DB, Kafka, trace, metrics, and browser state |
| Prompt/data leakage | Content minimization; encrypted Prompt storage; sanitized summary | Prompt, provider payload, raw Tool value, and private-reasoning canary tests |
| Model/Prompt supply-chain drift | Immutable versions/digests; review/evaluation gates; pinned execution | Stale digest, rollback, evaluation swap, and publication race tests |
| Cost manipulation/double charge | Idempotent usage key; immutable pricing snapshot; compensating corrections | Duplicate/reordered usage, concurrent budget, retry, and reconciliation tests |
| Trace data leakage | W3C propagation with bounded allowlist; no content attributes | HTTP/SSE/Python/Kafka/Connector propagation and attribute-bound tests |
| DoS through governance APIs | Cursor limits, bounded summaries, body limits, rate policy, batch limits | Limit, pagination, oversized payload, and concurrent mutation tests |
| Injection through policy metadata | Strict schemas, allowlists, endpoint/secret reference validation | Schema fuzzing, URL/credential payload, and unsafe metadata tests |
| Retention abuse | Tenant policy, tombstone-before-purge, legal retention guard | Deadline, purge retry, export, and system-scope deletion tests |

## Security invariants

- A client, model, Tool, MCP response, event, or runtime cannot select or widen tenant scope.
- No governance failure falls back to an unbounded budget, unversioned model/Prompt, raw logging, or permissive
  authorization.
- Audit and usage records expose identifiers, versions, digests, decisions, counts, and bounded summaries only.
- Provider credentials are resolved only by the existing secret resolver and are never persisted by Governance.
- Tenant suspension blocks new work while preserving historical pins and allowing audited cancellation/cleanup.

## Approval gate

The independent reviewer approved the architecture and security design with conditions: V2.7.0 DDL must pass
tenant-isolation and rollback contract tests; implementation must preserve data minimization; and all remaining
OEP quality and release gates remain mandatory. Release still requires executable abuse cases,
dependency/container/IaC scans with no HIGH/CRITICAL findings, secret-canary checks, compatibility checks, and
benchmark evidence.

## Decision record

The review decision is recorded in [Issue #99](https://github.com/CoderDongHuang/OpenEIP/issues/99#issuecomment-5507449833)
by `WriteBigBug` as `Approved with Conditions` on 2026-09-02.

## Runtime quota enforcement evidence

The runtime quota enforcement slice completed its security validation on 2026-09-05.

| Control | Implementation evidence | Result |
|---|---|---|
| Tenant authority | Context tenant is checked before policy lock; every reservation query, aggregate, FK, and release is tenant-leading | Passed |
| Atomic admission | Policy row is locked `FOR UPDATE` before aggregate reads and decision insertion; 8-way integration and 100-way benchmark contention did not oversell | Passed |
| Time authority | Java server clock selects the window and validates the `(0, 24h]` lease; caller-controlled window time is absent | Passed |
| Idempotency | `(tenant_id, quota_policy_id, idempotency_key)` is unique; identical facts replay and changed facts return `GOV-I-001` | Passed |
| Fail closed | Missing tenant/policy, stale policy version, invalid lease, and exhausted dimensions reject without permissive fallback | Passed |
| Data minimization | Rows and audit summaries contain bounded identifiers, counts, decisions, and correlation metadata only | Passed |
| Cross-tenant database guard | MySQL 8.4 rejects a tenant-two reservation referencing a tenant-one policy through the composite FK | Passed |
| Supply chain and secret scan | Existing Trivy script scanned 934 Git-relevant files for HIGH/CRITICAL vulnerabilities, misconfiguration, and secrets | Passed |

No dependency, public API, event schema, SDK, or Plugin SPI was added or changed by this slice. Release-candidate
container and deployment checks remain part of steps 14-17 and are not claimed here.
