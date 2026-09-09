# Issue #99: v0.7 Governance

> Status: Architecture/security review approved with conditions; implementation in progress
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
| 5 API/Database/UI Design | Governance OpenAPI v2, V2.7.0 schema, event contract, and management workspace | Complete 2026-09-01 |
| 6 Architecture Review | [Architecture review](issue-0099-architecture-review.md) and [Security review](issue-0099-security-review.md) | Approved with Conditions 2026-09-02 |
| 7 Implementation | Java Governance control plane, Python/Frontend compatibility, migrations and runtime quota slice | Complete; standards-audit fixes merged in PR #120 |
| 8 Unit Test | Governance unit/contract suites and coverage | Complete; 57 non-benchmark tests, 92.03% changed-module coverage |
| 9 Integration Test | H2/MySQL migration, rollback, tenant FK and Compose/release smoke | Complete for recorded release evidence; rerun required for any post-tag patch |
| 10 Benchmark | Quota admission and contention benchmark | Complete; P99 5.427 ms, 20/100 allowed at limit, zero oversell |
| 11 Security Review | Governance threat model, abuse cases, Trivy and secret scans | Complete with conditions; post-audit scan evidence must remain attached to patch release |
| 12 Quality Gate | [Quality gate](issue-0099-quality-gate.md) | Complete for implementation slice; release gate is tracked in [v0.7 checklist](../14-release/v0.7.0-alpha-checklist.md) |
| 13 Docs Update | RFC/ADR/SDD/API/DB/UI/test/release metadata | Complete; release checklist retains historical pending items where rerun evidence is absent |
| 14 Pull Request | Implementation PRs #102/#118/#120 | Complete |
| 15 Code Review | GitHub review records for the implementation and standards-audit PRs | Pending external verification; merge history alone is not review evidence |
| 16 Merge | PR #120 merged to `main` as `b373a15` | Complete 2026-09-08 |
| 17 Release | `v0.7.0-alpha` GitHub Pre-release | Complete 2026-09-08; tag is immutable and must not be rewritten |

### Runtime quota enforcement slice

Steps 7-13 are complete for runtime quota enforcement as of 2026-09-05. The slice adds atomic token, cost,
request, and concurrency admission; expiring/releasable leases; stable `GOV-B-001` denial and `GOV-I-001`
idempotency conflict behavior; tenant-scoped MySQL persistence; and transactional audit evidence. Its quality
record is [issue-0099-quality-gate.md](issue-0099-quality-gate.md), and benchmark evidence is
[v0.7-governance-quota-benchmark.json](../13-testing/results/v0.7-governance-quota-benchmark.json).

The full v0.7 release record is maintained in the release checklist. Any later
standards-audit fix must use a new immutable patch/pre-release tag; it must not
move or rewrite `v0.7.0-alpha`.

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
implementation.

## API/Database/UI Design Record

The design packet is complete:

- API: [governance-v2.openapi.yaml](../06-api/governance-v2.openapi.yaml) defines additive tenant, audit,
  model, Prompt, usage, budget, and trace endpoints with server-derived tenant scope.
- Database: [governance-schema.md](../05-database/governance-schema.md) defines the `V2.7.0` table groups,
  tenant-leading indexes, immutable history, idempotency keys, and migration/rollback constraints.
- Events: [governance.event.v1.schema.json](../../contracts/events/governance.event.v1.schema.json) defines
  sanitized policy, audit, usage, budget, and trace envelopes.
- UI: [governance-workspace.md](../08-ui/governance-workspace.md) defines administrative surfaces, permission
  matrix, state handling, accessibility, and data-minimization rules.

With Step 6 approved with conditions, the design can now be converted into Java, Python, Frontend, and migration
code. The conditions below remain mandatory throughout implementation and release.

## Architecture/Security Decision Record

Independent reviewer `WriteBigBug` recorded `Approved with Conditions` on 2026-09-02 in
[Issue #99](https://github.com/CoderDongHuang/OpenEIP/issues/99#issuecomment-5507449833). The mandatory
conditions are: V2.7.0 DDL must pass tenant-isolation and rollback contract tests; runtime implementation must
preserve the documented data-minimization rules; and all remaining OEP quality and release gates remain
mandatory. The project may enter implementation, but these conditions are tracked through the quality and
release gates.
