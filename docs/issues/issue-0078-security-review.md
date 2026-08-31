# Security Design Review: Issue #78 v0.6 Agent

> Review packet status: Complete with moderate upstream residual risk
> Decision: HIGH/CRITICAL security gate passed; Docusaurus development chain retains unfixable MODERATE uuid findings
> Date: 2026-08-31

## Assets and trust boundaries

Assets include tenant data, Agent/Tool definitions, durable checkpoints, Memory content/vectors, Connector and
MCP credential references, external side effects, evaluation fixtures, and audit evidence. Trust boundaries are
browser-to-Java, Java-to-Python capability, worker-to-Tool adapters, Memory metadata-to-vector store, MCP
gateway-to-remote Server, Supervisor-to-Worker Handoff, and evaluator-to-candidate run.

## Threat review

| Threat                       | Required control                                                                          | Required evidence before release                                    |
| ---------------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Prompt/tool injection        | retrieved/MCP/Worker content is untrusted; exact Tool grant and schema recheck            | injection corpus for all five Agents; zero unauthorized calls       |
| Tenant/resource confusion    | server-derived tenant; tenant-first SQL/cache/vector/session keys; authorization per call | cross-tenant negative suite and query/index inspection              |
| Excess authority             | signed short-lived narrowed capability; exact version/digest; no discovery authority      | tamper, expiry, replay, confused-deputy tests                       |
| Planner/Multi-Agent DoS      | hard depth/fan-out/concurrency/step/time/token/cost/retry/repeated-edge limits            | boundary, crash-recovery, cancellation, and load tests              |
| Duplicate side effects       | idempotency keys, fencing, attempt history, no automatic destructive retry                | crash between side effect/checkpoint and reconciliation tests       |
| Memory poisoning/leakage     | provenance, purpose, verification state, sensitivity filter, quarantine, citations        | poison/retrieval tests and provenance UI checks                     |
| Retention/deletion failure   | immediate tombstone, idempotent purge, lineage purge, backup deadline                     | Redis/Milvus/derived reconciliation and tenant-delete test          |
| MCP SSRF/rebinding/redirect  | gateway, HTTPS, allowlist, IP rejection, DNS pinning, redirect/port policy                | controlled malicious DNS/redirect/metadata endpoint tests           |
| MCP credential/session mixup | secret references, audience/scope, tenant/auth-keyed sessions and caches                  | session-isolation and token-forwarding negative tests               |
| Schema/capability drift      | digest pin, quarantined discovery, mapping suspension                                     | drift during active and new runs tests                              |
| Data exfiltration            | output classification/size limits, safe events, Tool resource scopes                      | canary secrets across API/SSE/Kafka/log/metric/UI checks            |
| SQL/BI unsafe execution      | AST/dialect validation, read-only default, limits, approval, safe chart schema            | multi-statement, comment, DDL/DML, row-policy, script payload tests |
| Evaluation manipulation      | immutable versions, pinned fixtures/scorers, grader identity, safety override             | baseline/candidate swap, fixture drift, scorer version tests        |

## Data minimization and observability

Never persist or emit chain-of-thought, provider prompts, raw credentials/tokens, MCP auth headers, SQL/document
content, raw Tool arguments/results, or unrestricted Memory/evaluation content. Audit records keep IDs, versions,
digests, decisions, safe reason/error codes, counts, duration, and bounded redacted summaries. Security scans
must inspect Java logs, Python logs, Kafka/DLQ, traces, metrics, database rows, browser state, and exports.

## Approval gate

The security design covers the required boundaries and was independently rechecked after the RFC/ADR
decisions. It is approved as an implementation-entry design gate. Documentation alone is not the final OEP
security gate: release still requires executable abuse cases, dependency/container/IaC scans with no
HIGH/CRITICAL finding, secret canary scans, and zero deterministic safety regression in Evaluation.

## Executed evidence

| Area | Evidence | Result |
|---|---|---|
| Capability boundary | Python Agent v2 tests cover HMAC tamper rejection, expiry, nonce replay protection, narrowed grant intersection and tenant isolation | Passed |
| Runtime bounds | Bounded Search execution and cancellation/limit paths are covered by the Python Agent v2 suite | Passed |
| MCP boundary | Managed MCP fixture tests reject private addresses and unsafe redirects; gateway authentication and session scoping are covered | Passed |
| Evaluation safety | Deterministic 500-case corpus, three repeats, 1,500 evaluated cases, zero errors; benchmark artifact is linked below | Passed |
| Java persistence | MySQL 8.4 Testcontainers validates the Agent migration, tenant-first indexes, candidate lifecycle fields and rollback | Passed |
| Source/container scan | Trivy Git-valid source snapshot: 797 files, 3.47 MB; Java and Python runtime images also scanned | Passed; zero HIGH/CRITICAL vulnerabilities, misconfigurations or secrets |
| Python dependencies | `pip-audit -r requirements.lock` | Passed; no known vulnerabilities |
| Frontend dependencies | Frontend `npm audit --audit-level=high` | Passed; no HIGH/CRITICAL findings |
| Website dependencies | Local safe `image-size@2.0.3` fork removes the affected `image-size@2.0.2` graph; `npm audit --audit-level=high` exits 0 | Passed at HIGH/CRITICAL policy; 18 MODERATE `uuid` findings remain |

## Residual and unexecuted checks

- The local safe `image-size` fork is an interim control until Docusaurus publishes an upstream fix;
  it must be reviewed when upgrading Docusaurus and must retain its bounded parser tests.
- The website audit still reports 18 MODERATE `uuid` findings through the Docusaurus development
  chain, with no available fix. They do not violate the current no-HIGH/CRITICAL release policy but
  remain a supply-chain residual risk for explicit tracking.
- Playwright desktop/mobile checks passed with no horizontal overflow, no broken images, one H1, and
  successful navigation from the home page to `/OpenEIP/docs/intro`.
- The complete Docker Compose stack passed health checks and `scripts/release_smoke.py` passed all
  Auth, File, OCR, Parsing, Knowledge, Embedding, RAG, Chat, Agent and Workflow flows.
- The live hybrid integration test passed against Compose Milvus 2.6.0 and Elasticsearch 8.19.0,
  including tenant/base scoping, source traceability and document deletion.

## Evidence links

- Benchmark: `docs/13-testing/results/v0.6-agent-benchmark.json`
- Test plan: `docs/13-testing/v0.6-agent-test-plan.md`
- Quality gate: `docs/issues/issue-0078-quality-gate.md`
