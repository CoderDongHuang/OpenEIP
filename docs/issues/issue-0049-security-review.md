# Security Review: Issue #49 RAG

> Status: Passed | Date: 2026-07-22

| Threat | Control | Evidence |
|---|---|---|
| Cross-tenant retrieval | repository filters tenant and knowledge base before candidate scoring | mixed tenant/base service and repository tests |
| Direct prompt injection | canonical system policy precedes an escaped, delimited user question | prompt structure and malicious-query tests |
| Indirect prompt injection | retrieved text is escaped, bounded, and explicitly labelled untrusted | context-breakout fixture and context-limit tests |
| Citation forgery | provider chunk IDs are resolved only through the retrieved context allowlist | unknown and duplicate citation tests |
| Vector poisoning | query vector must have exact dimension, finite values, and unit L2 norm | empty/dimension/NaN/norm provider tests |
| Input exhaustion | observed 32 KiB body, 2,000-character query, context/answer, and `topK` limits | API body/query/topK and service configuration tests |
| JSON ambiguity | strict UTF-8 and types; BOM, duplicate key, unknown field, and non-finite value rejection | parameterized decoder tests |
| Secret/error disclosure | authentication precedes decoding; provider/repository failures use stable envelopes | invalid credential and secret-bearing failure tests |
| Provider substitution | deterministic offline provider is explicit; external provider requires injection | startup configuration test |

## Residual Risk

- The deterministic answer fixture validates grounding mechanics, not semantic answer quality,
  hallucination rate, bias, multilingual quality, or a production model supply chain.
- The in-memory vector repository is process-local and forbidden in the production profile. Milvus
  authentication, TLS, capacity, backup, and failure behavior remain promotion gates.
- Java remains the knowledge-base authorization point. A compromised internal caller with the shared
  credential could submit a valid tenant/base pair; service identity and per-call authorization remain
  required before production exposure.
- Prompt separation and citation validation reduce injection impact but cannot prove that every future
  model provider follows policy. Provider-specific adversarial evaluation is required before replacing
  the deterministic fixture.

Trivy 0.72.0 found 0 HIGH/CRITICAL vulnerabilities in the Git-relevant repository snapshot, Java
fat-JAR runtime, and Debian 13.6/Python runtime image. Repository secret and misconfiguration scans
also returned zero findings. `pip-audit` and Frontend `npm audit` found zero known vulnerabilities;
the Docusaurus development chain reported 18 Moderate issues and no HIGH/CRITICAL issue.
