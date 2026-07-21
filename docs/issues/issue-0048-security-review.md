# Security Review: Issue #48 Embedding

> Status: Passed | Date: 2026-07-22

| Threat | Control | Evidence |
|---|---|---|
| Input exhaustion | 128 KiB observed-body limit, 32 chunks, 8,192 chars per text | API body/batch/text limit tests |
| Cross-tenant vector leakage | repository filters tenant and knowledge base before scoring or delete | mixed-tenant/base search and delete tests |
| Dimension/vector poisoning | exact dimension, finite value, and unit L2 norm validation before upsert | provider batch/dimension/NaN/norm tests |
| Job replay/collision | tenant/job key plus complete input/model fingerprint; bounded 1,000-entry cache | exact replay and changed-input collision tests |
| JSON ambiguity | strict UTF-8, no BOM, duplicate key rejection, unknown-field rejection | parameterized API decoder tests |
| Provider supply chain | deterministic default has no network/key; other providers require explicit injection | startup configuration tests |
| Secret/error disclosure | internal token checked before parsing; provider exceptions map to stable errors | invalid token and secret-bearing failure tests |
| Memory adapter in production | startup rejects `InMemoryVectorRepository` for production | production composition test |
| Similarity manipulation | normalized query required; deterministic score/chunk tie break | invalid query and equal-score tests |

## Residual Risk

- In-memory vectors and job idempotency do not survive restart. This is explicit MVP behavior and the
  production profile fails closed until durable adapters are injected.
- The deterministic hash provider validates orchestration, not semantic recall, bias, language quality,
  or a production model supply chain.
- Milvus capacity, backup/recovery, authentication, TLS, and multi-node failure behavior remain v0.3
  promotion gates under ADR-0003/0004.
- The synchronous API produces event-ready metadata but does not claim Kafka publication or outbox
  durability.

Trivy 0.72.0 found 0 HIGH/CRITICAL vulnerabilities in the committed snapshot, Java fat-JAR runtime,
and Debian 13.6/Python runtime image. `pip-audit` found 0 known vulnerabilities.
