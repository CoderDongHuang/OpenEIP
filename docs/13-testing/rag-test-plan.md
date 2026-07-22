# RAG Test Plan

> Issue: #49 | Gate: coverage >= 80%, 1,000-record RAG P99 < 50 ms

| Layer | Coverage |
|---|---|
| Unit | prompt separation, query vector validation, empty context, citation allowlist |
| API | auth/identity, strict JSON, body/query/topK bounds, response envelope |
| Repository | tenant/base filtering occurs before scoring and context assembly |
| Contract | runtime OpenAPI and source operation synchronization |
| Benchmark | 1,000 vectors, exact quality fixture, 5 warmups + 100 samples |
| Security | direct/indirect injection, leakage, forged citation, provider secret/error mapping |

The quality fixture proves pipeline grounding mechanics only. It does not claim production model
faithfulness, answer quality, or Milvus capacity.
