# Embedding Test Plan

> Issue: #48 | Gate: coverage >= 80%, 32-text batch P99 < 50 ms

| Layer | Coverage |
|---|---|
| Unit | deterministic provider, norm/dimension/finite validation, idempotency collision |
| API | auth and identity, strict body, batch/text/body bounds, standard envelope |
| Repository | upsert replacement, scoped cosine search, deterministic tie, document delete |
| Contract | runtime OpenAPI and `embedding.job.completed.v1` Schema synchronization |
| Benchmark | 32 texts x 64 dimensions, 5 warmups + 100 samples; exact nearest fixture |
| Security | cross-tenant leakage, resource exhaustion, dimension poisoning, secret-free errors |

The memory repository is a contract adapter. No Milvus capacity, availability, backup, or semantic
model quality claim is made by these tests.
