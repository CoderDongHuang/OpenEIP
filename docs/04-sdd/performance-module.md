# Performance Module SDD

## Scope

2026-10-26: full-module design is **Proposed**, awaiting RFC/ADR decisions and
pre-implementation architecture approval. The merged bounded GET harness is
one component, not delivery of the complete Performance module.

The complete scope is load testing, tuning, high availability, scaling, and
stability, as required by OEP, PRD and SAD. The following sections define the
corrective design. Numeric targets are proposed acceptance criteria, not
pre-existing commitments or measured results.

## Design

`benchmark/run_benchmark.py` uses only the Python 3.12 standard library. A
thread pool provides bounded concurrency. DNS is resolved once before the run
and the resulting address is pinned to the TCP/TLS connection, preventing a
second resolution from being redirected to a different network. Each request
has an explicit timeout and reads in bounded chunks up to
`maxResponseBytes + 1`, so an unexpectedly large body is classified without
being persisted. Response bodies, headers, credentials, and payloads are never
included in evidence. Automatic redirects are disabled, so 3xx responses are
recorded as failures rather than silently changing the target.

URL syntax, port ranges, DNS resolution, and the complete resolved address set
are validated before requests start. The selected resolved address is pinned
for the run to prevent DNS rebinding; an empty resolution result is rejected.
The bounded reader truncates over-reading adapters at the sentinel byte so the
response-size limit also holds for non-conforming test transports.

The target URL is mandatory, limited to HTTP(S), and cannot contain user
credentials or fragments. DNS-resolved private, loopback, link-local,
reserved, and multicast addresses are rejected unless the operator explicitly
passes `--allow-private-network` for a local fixture. Requests, concurrency,
timeouts, warmups, and response size all have hard upper/lower bounds.

## Result Contract

The JSON result has `schemaVersion`, request configuration, a redacted target
(`scheme` and `host` only), `sampleCount`, elapsed time, throughput,
`latencyMs.p50/p95/p99/min/max`, `outcomes`, `statusCodes`, `errorCount`, and a
`result` of `PASS` only when every measured request returned 2xx and stayed
within the response limit. When `--max-p99-ms` is supplied, the result also
requires P99 to be at or below that threshold. Percentiles use nearest-rank over all completed
requests, including errors, to prevent failed requests from disappearing from
the latency distribution.

The versioned machine-readable contract is maintained at
`contracts/performance/performance-benchmark-result.v1.schema.json`; the API
design decision and non-applicability record are in
`docs/06-api/performance-benchmark.md`.

## Operational Boundaries

The shell wrapper requires the target URL as its first argument. Operators must
run against a non-production fixture unless a change review explicitly approves
the target. Benchmark output is evidence for regression comparison and release
review; it is not a substitute for multi-node, database, provider, chaos, or
capacity testing. Those capabilities are required for the complete module and
must have separate implementation and deployment evidence before completion.

The repository includes `benchmark/local_fixture.py` for deterministic local
health responses; it binds loopback by default and is intended only for the
documented baseline procedure.

## Full-module responsibility and ownership

| User story / PRD requirement | Delivery | Implementation location |
|---|---|---|
| Repeat business workloads and detect regression, PERF-001 | Authenticated workloads, domain benchmarks, SSE first-token/total latency, reference comparison | `benchmark/` operator utilities; no public load-generation API |
| Locate bottlenecks and tune safely, PERF-002 | Resource/trace/lock measurements, bounded pools/queues and before/after evidence | Existing Java/Python modules, preserving layered ports |
| Survive application node failure, PERF-003/007 | Three replicas, stable credentials, shared state/files, readiness and draining | Runtime configuration, shared-state adapters and Kubernetes |
| Add/remove capacity, PERF-004 | HPA 3→6→3, PDB, resource and database connection budgets | `deploy/kubernetes/` (to implement) |
| Recover consistently, PERF-005/008 | Soak, fault injection, backup/restore, cross-index repair and data HA | Validation scripts and deployment runbooks |
| Verify operation and publication, PERF-006/009 | Prometheus/Grafana/OTel, alert exercises, candidate-to-image identity | `deploy/monitoring/` (to implement), CI and evidence |

Java/MySQL remains authoritative for business state, permissions, metadata,
quota and audit. Python must not write Java business tables. Redis holds
shared limits, short-lived coordination and derived results; ES/Milvus are
rebuildable retrieval stores. Object content stays behind ObjectStorage.
Kafka uses existing eventId/outbox/inbox semantics. No second Connector SPI,
new public business API or plugin format is introduced.

## Contracts before implementation

The existing GET v1 CLI/Schema stays compatible. New management endpoints,
workload inputs and separate result types are specified in
[the operations contract](../06-api/performance-operations.md). WP1 must first
commit their executable OpenAPI and JSON Schemas; new business fields cannot
silently change GET result v1. Database fields, indexes, algorithms and rollback
are specified in [the state design](../05-database/performance-state.md).

The management UI is Grafana, with controlled access on the management network;
there is no new business-workspace page. Existing SDK methods, event Schemas
and Plugin SPI remain compatible. Full-module DB/management/UI effects are no
longer marked globally not applicable.

## Business load and regression design

Use a separate authenticated runner, explicit isolated environment/allowlist,
test tenants and fixed dataset hashes. Credentials come from protected
environment variables, never command arguments, URLs or reports. Validate
authentication and data before measuring, then clean up only resources owned
by the run; cleanup failure fails the scenario. Never delete a whole database
or existing environment volumes.

Required scenarios: login/refresh, file upload/download/hash verification,
OCR, parsing, Embedding, full-text/vector/hybrid RAG, Chat/Agent SSE,
Workflow trigger/approval/retry, Connector idempotent operations and Marketplace
discovery. Report separately; health throughput is not business throughput.
SSE checks event ordering, first-token and total latency, termination and
client cancellation. Errors and broken streams count as failures.

Collect business reference measurements on `993569a` before optimization,
then candidate measurements on the same CPU/RAM, dataset, dependency/model/
Provider versions and seed. Each scenario runs three rounds, each
with 1,000 measured operations after warmup at concurrency 1/4/16.
OEP's no-regression gate applies: median-of-rounds P99 must not increase and
throughput must not decrease, with correctness and error gates independently enforced.
Record measurement noise and profiles; noise does not authorize a silent tolerance
or a PASS on a measured regression. Investigate and rerun the complete paired measurement
when the setup is unstable, retaining failed runs and the reason for rerunning.
Missing reference, mismatched environment, insufficient samples or absent
metrics is INCOMPLETE and exits nonzero. Deterministic CI results and real
provider/retrieval capacity results remain separate; neither proves model quality.

## Tuning and bounded execution

Measure CPU, memory, descriptors, database/external latency and lock contention
before selecting an optimization. Initial proposed budgets:

- Java Hikari max 10 connections/replica, acquisition timeout 2s. Compute
  `maxReplicas × pool + workers + migration + reserve` against DB capacity.
- Reuse bounded HTTP clients, configure connect/read/total deadlines; provider
  calls cannot exceed the request's remaining budget.
- Python work concurrency 16/replica, queue 32, queue timeout 1s. Reject overload
  with existing 503 semantics; security limits retain 429. Probes must remain
  responsive under exhausted work capacity; CPU work cannot block the event loop.
- Remove Embedding's process-wide lock across Provider I/O only after shared
  same-key coordination exists; unrelated keys execute with bounded parallelism.
- Cache keys bind tenant, input digest, access scope and model/version; no
  cross-tenant answer cache. Any retry has bounded jitter/backoff and is allowed
  only for idempotent operations before output or external side effects.
- SIGTERM immediately fails readiness, stops new work/scheduling and drains for
  at most 30s, with Kubernetes grace 45s. Never transparently replay SSE after
  tokens or non-idempotent external actions with unknown outcomes.

Each optimization needs before/after data, configuration diff, traces and a
rollback procedure; configuration changes alone do not constitute tuning evidence.

## Shared state and recovery semantics

Redis limits aggregate across replicas. Redis failure must fail closed, without
process-memory fallback. Following failover or loss of connection continuity,
block at least a full limit window to avoid admitting against lost counters.
Embedding cache loss may recompute deterministic upserts; the same jobId with
a different fingerprint still conflicts. Without provider idempotency,
recovery may invoke/charge again and cannot claim exactly-once billing.

Audit every Workflow cron/execution, Agent scheduler and outbox path for
concurrent workers. Combine leases/fencing with short database transactions,
conditional state transitions and unique idempotency keys. Do remote I/O
outside row locks; validate fencing token at commit. Unknown external side
effects require reconciliation, not automatic replay without external deduplication.

ES/Milvus partial writes retain recoverable work and reconcile by tenant,
document and version: rebuild from authoritative documents, idempotently repair
both indexes, then verify. Reports retain counts/digests rather than source text.

## Deployment and scaling

See [the deployment design](../10-deployment/v0.9-performance-ha.md).
Java/Python each have three replicas, Frontend two; readiness/startup/liveness
are separate, with PDB, node spread and HPA 3–6. Multi-replica startup preflights
shared stable keys, Redis, persistent retrieval, HA RWX files and non-development
secrets; missing requirements prevent readiness. Local Compose remains a
development entry and cannot serve as multi-node evidence.

MySQL, Redis, Kafka, Milvus, ES and MinIO/RWX each require topology/configuration,
actual failover and restore evidence. Managed infrastructure is an allowed
topology, not an exemption from acceptance. Minimal Kubernetes HA/HPA realizes
SAD's existing architecture in v0.9; full Helm distribution, SSO/LDAP and LTS
remain v1.0. Keep ObjectStorage using tested coherent HA RWX storage before
object-storage productization; MinIO HA is still required for retrieval infrastructure.

## Observability and threat model

Prometheus collects bounded route-template QPS, errors, latency histograms,
active/queued/rejected work, dependency latency, pools, lease conflicts/expiry,
recovery time and CPU/memory/GC/disk. Grafana provides overview, business latency,
dependencies/pools, scaling and scheduler/recovery views. OTel links Gateway,
Java, Python and dependencies through traceparent; never put credentials or
source text in spans. Metrics cannot label tenantId/userId/jobId/raw URLs.

| Threat | Required control and verification |
|---|---|
| Unauthorized load/fault actions | Isolated CLI only, target allowlist, namespace/run ownership checks; fault tools default to read-only plans |
| Cross-replica limit bypass / tenant key collision | Atomic tenant-scoped state, contention and cross-tenant tests, failure closed |
| Management exposure / sensitive traces | Management network/NetworkPolicy, authenticated management UI, redaction/cardinality limits |
| Restart/rotation invalidates credentials | Shared RSA/internal/capability keys, consistent configuration and rolling token tests |
| Unbounded queues / missed cancellation | Work/queue/pool limits, deadlines, disconnect and SIGTERM tests |
| Stale workers / duplicate events | Fencing, conditional commit, unique keys, outbox/inbox; no unsupported replay claims |
| Backup/report leakage | Least privilege, encrypted backups, no production data, restored authorization checks |

## Implementation work packages and review gates

| Package | Prerequisite | Deliverable and required proof |
|---|---|---|
| WP0 Design decision | This design package | RFC-0012 public discussion/vote, ADR-0021 acceptance, independent architecture approval; no early implementation |
| WP1 Baselines/contracts | WP0 | Implement the reviewed OpenAPI/result Schema contracts and semantic validators; dataset, business reference measurements and GET v1 compatibility |
| WP2 State/tuning | WP1 | Redis/leases/clients/bounded work, real double-instance integration, unit+integration coverage ≥80%, before/after comparison |
| WP3 HA/scaling | WP2 | Kubernetes/Secrets/RWX/probes/HPA/data HA/monitoring, multi-node faults and 3→6→3 |
| WP4 Stability/release | WP3 | Soak/faults/restore/reconciliation, independent security review, six gates, implementation PR approval/merge, tag CI |

The [test plan](../13-testing/v0.9-performance-test-plan.md) and
[release checklist](../14-release/v0.9.0-alpha-checklist.md) define evidence.
A written design is reviewable, not implemented or verified functionality.
