# Issue #99: v0.7 Governance

> Status: Governance SDD accepted; API/Database/UI design pending
> Release train: v0.7 Governance
> Date: 2026-08-31

## Objective

Deliver the v0.7 Governance release train for enterprise governance of OpenEIP AI workloads.

## Scope

- Tenant, organization, membership, quota, and tenant-context governance
- Immutable, queryable audit events for platform, data, Agent, Tool, and administrative actions
- Model registry, provider policy, capability constraints, routing metadata, and secret references
- Prompt registry with versioning, review, evaluation linkage, publication, and rollback metadata
- Token usage, cost attribution, budgets, quotas, and threshold alerts
- Trace and correlation metadata across Gateway, Java, Python, Agent, Workflow, and Connector paths
- Administrative Frontend surfaces and stable REST/API contracts

Marketplace distribution, high availability, Kubernetes, SSO/LDAP, and LTS hardening remain outside this
issue unless an accepted RFC changes the boundary.

## OEP status

| Step | Evidence | State |
|---|---|---|
| 1 Issue | GitHub #99 | Complete 2026-08-31 |
| 2 RFC | RFC-0009 and PR #102 | Accepted 2026-09-01 |
| 3 ADR | ADR-0015 through ADR-0018 | Accepted 2026-09-01 |
| 4 Module Design | Governance SDD | Complete 2026-09-01 |
| 5 API/Database/UI Design | Governance contracts and surfaces | Not started |
| 6 Architecture Review | Independent architecture and security review | Not started |
| 7-13 Implementation and validation | Java/Python/Frontend and quality evidence | Not started |
| 14-17 Delivery | Pull Request, Review, Merge, Release | Not started |

## Acceptance criteria

- Existing v0.1-v0.6 APIs and Agent/Workflow contracts remain backward compatible.
- Every governed resource is tenant-scoped and cross-tenant access is rejected by default.
- Audit records are tamper-evident, traceable by request/trace ID, and do not persist secrets, prompts, raw
  tool values, or private reasoning.
- Model and Prompt publication is versioned, reviewable, reversible, and linked to evaluation evidence.
- Cost usage is idempotent, attributable, and tested against budget and quota boundaries.
- Trace context survives Java-to-Python and asynchronous event boundaries.
- CI, security, compatibility, and quality gates pass before release candidate work begins.

## Decision Record

RFC-0009 and ADR-0015 through ADR-0018 were accepted by the Bootstrap Maintainer on 2026-09-01. The
acceptance authorizes the next OEP stage, Governance SDD, but does not waive API/Database/UI design,
independent architecture/security review, implementation, testing, compatibility, security, benchmark,
quality gate, or release requirements.

## Module Design Record

The Governance Sub-SDD is [governance-module.md](../04-sdd/governance-module.md). It fixes the Java
control-plane authority, tenant context propagation, audit and hash-chain boundary, model/Prompt lifecycle,
idempotent usage/cost ledger, trace sanitization, failure policy, and the design deliverables required before
implementation. The API, database, event, and Frontend contracts remain the next OEP stage.
