# Security Design Review: Issue #78 v0.6 Agent

> Review packet status: Complete  
> Decision: Security controls specified; independent approval and executable evidence pending  
> Date: 2026-07-26

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

The security design covers the required boundaries, but documentation alone is not a passed OEP security gate.
Implementation remains blocked until RFC/ADR decisions and independent architecture/security approval. Release
also requires executable abuse cases, dependency/container/IaC scans with no HIGH/CRITICAL finding, secret
canary scans, and zero deterministic safety regression in Evaluation.
