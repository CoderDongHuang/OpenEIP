# Embedding Module Design (Sub-SDD)

> Version: 2.0 | Date: 2026-07-24 | Status: Implemented for v0.3
> Issue: [#65](https://github.com/CoderDongHuang/OpenEIP/issues/65) | RFC: [RFC-0005](../11-rfc/rfc-0005-production-knowledge-retrieval.md) | ADR: [ADR-0009](../12-adr/adr-0009-hybrid-retrieval-storage.md)

## 1. Responsibilities and Boundaries

The Python Embedding module validates bounded text batches, invokes an `EmbeddingProvider`, verifies
finite normalized vectors, and upserts tenant/base/document/chunk metadata through a
`VectorRepository`. It does not parse documents, authorize users, or select RAG context. The production
adapter owns Milvus vector persistence and coordinates the Elasticsearch chunk index.

## 2. API and Limits

The source contract is [embedding-v1.openapi.yaml](../06-api/embedding-v1.openapi.yaml). The internal
`POST /api/v1/embedding/batches` endpoint requires the shared internal token and canonical UUID
identity headers. A request contains one job, knowledge base, document, and 1-32 unique chunks. Each
text is 1-8,192 characters and the aggregate UTF-8 body is at most 128 KiB. Unknown JSON fields,
non-canonical UUIDs, invalid dimensions, blank/control-only text, and duplicate chunk IDs fail closed.

## 3. Ports and Adapters

- `EmbeddingProvider.embed(texts)` returns model name, version, dimension, and one vector per input.
- `VectorRepository.upsert/search/delete_document` always requires tenant and knowledge-base scope.
- `DeterministicEmbeddingProvider` is the offline default for development and CI. It is a reproducible
  contract fixture, not a semantic-quality model.
- `InMemoryVectorRepository` is allowed only outside the `production` environment. v0.3 production uses
  Milvus HNSW/COSINE with an Elasticsearch chunk index. Production startup rejects the memory backend.
- An OpenAI-compatible embedding adapter reads its API key only from deployment configuration and
  normalizes every returned vector before the common validation gate.

Production configuration uses `OPENEIP_EMBEDDING_PROVIDER=openai`,
`OPENEIP_EMBEDDING_REPOSITORY_BACKEND=milvus`, `OPENEIP_OPENAI_API_KEY`,
`OPENEIP_OPENAI_BASE_URL`, `OPENEIP_EMBEDDING_MODEL`, `OPENEIP_MILVUS_URI`,
`OPENEIP_MILVUS_TOKEN`, and `OPENEIP_ES_HOST`. Secrets have no defaults in production.

Provider and repository adapters are constructor-injected. No provider API key is accepted in an API
body, result, event, metric, or log.

## 4. Idempotency and Event

`jobId` plus a SHA-256 fingerprint of tenant/base/document/chunks/model configuration forms the
idempotency record. An exact replay returns the prior result without another provider or upsert call;
reuse with changed input returns `EMB-E-003`. The MVP store is bounded to 1,000 jobs and is process
local; durable cross-restart jobs belong with the later broker/outbox adapter.

[`embedding.job.completed.v1.schema.json`](../../contracts/events/embedding.job.completed.v1.schema.json)
is the only completion contract. It contains identifiers, counts, model/version/dimension, and no text
or vector values. The synchronous API returns event-ready data but does not claim Kafka publication.

## 5. Vector Semantics

All vectors must have the configured dimension, contain finite numbers, and have L2 norm within
`1e-6` of 1. Search uses cosine similarity (dot product for normalized vectors), filters by tenant and
knowledge base before scoring, uses deterministic score/chunk tie-breaking, and limits `topK` to 100.
Delete removes only one tenant/base/document scope.

## 6. Quality, Security, and Compatibility

Tests cover API authentication/limits, deterministic output, provider poisoning, idempotency,
cross-tenant search/delete, contract synchronization, and benchmark throughput/latency. The batch
P99 target is 50 ms for 32 texts at 64 dimensions and search correctness must return the exact nearest
fixture. Changes are additive except renaming the not-yet-released stacked completion event to the
Issue #48 canonical name.
