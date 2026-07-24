# Knowledge Retrieval Storage Schema

> Version: 1.0 | Issue: #65 | Stores: Milvus 2.6, Elasticsearch 8

## Shared identity

Every Chunk record contains canonical `tenantId`, `knowledgeBaseId`, `documentId`, `chunkId`,
`sourceSha256`, `pages`, `startChar`, and `endChar`. Upserts use the scoped logical identity
`tenant/base/chunk`; deletes require `tenant/base/document`. Global search is forbidden.

## Milvus

Collection `openeip_chunks_v1` stores a normalized fixed-dimension FLOAT_VECTOR and bounded citation
metadata. It uses HNSW with COSINE (`M=16`, `efConstruction=200`) and query `ef >= 64`. Tenant/base
scalar predicates are included in the search expression before ANN ranking.

## Elasticsearch

Index `openeip-chunks-v1` uses strict mappings. Identity/hash fields are `keyword`, page/range fields
are numeric, and `text` uses the standard analyzer. Full-text queries are a bool query whose tenant/base
terms are filter clauses and whose text match is the scoring clause.

## Consistency

Ingestion writes Milvus then Elasticsearch. Failure of the second write triggers scoped vector deletion
and a failed processing state. Rebuilding a READY document replaces both stores. Periodic reconciliation
is required before stable release; v0.3 alpha records this as an operational limitation.
