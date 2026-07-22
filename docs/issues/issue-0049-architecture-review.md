# Architecture Review: Issue #49 RAG

> Decision: Approved | Date: 2026-07-22

| Check | Result |
|---|---|
| SAD boundary | Python owns retrieval/prompt/model orchestration; Java owns membership authorization |
| Repository reuse | Same injected Embedding repository; no global-search side channel |
| Prompt boundary | system, user question, and untrusted context are structurally separated |
| Citation integrity | provider returns IDs; runtime resolves only against retrieved allowlist |
| Offline default | deterministic answer provider requires no external key or network |
| Compatibility | additive internal API and ports; no public SPI or storage promotion |

The review permits coding. Streaming belongs to Chat #50, tools belong to Agent #51, and production
model quality/Milvus promotion remain outside this module.
