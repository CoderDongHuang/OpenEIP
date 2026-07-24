# ADR-0009: Milvus and Elasticsearch Hybrid Retrieval

> Status: Accepted | Date: 2026-07-24 | Issue: #65

## Context

Vector search handles semantic similarity but is weak for identifiers and exact terms. Full-text search
cannot reliably retrieve paraphrases. Citation display also needs durable chunk text and provenance.

## Decision

Use Milvus HNSW/COSINE for normalized vectors and Elasticsearch for full-text chunk records. Both
stores carry tenant, knowledge-base, document, chunk, and source-hash identity. Each engine filters the
scope before ranking. Merge ranked lists with RRF using `1 / (60 + rank)` and break ties by chunk ID.

## Consequences

The design adds two operational dependencies and requires compensation/reconciliation for dual writes.
It avoids vendor-specific ranking scores leaking into a combined score and preserves deterministic
results. Elasticsearch is the citation text source; Milvus never becomes an authorization boundary.
