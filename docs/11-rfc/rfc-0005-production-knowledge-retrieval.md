# RFC-0005: Production Knowledge Retrieval

> Status: Accepted for implementation | Issue: #65 | Version: v0.3

## Decision

OpenEIP v0.3 keeps Java as the authorization/control plane and Python as the parsing and retrieval data
plane. Java passes canonical tenant, user, base, and document identities only after membership checks.
Python extracts bounded text, generates embeddings, and dual-writes chunk records to Milvus (vectors)
and Elasticsearch (text and citation metadata). Query execution applies tenant and knowledge-base
filters in both engines before deterministic reciprocal-rank fusion (RRF).

Development and CI may inject deterministic providers and an in-memory repository. A production
process must reject these adapters. Provider credentials are deployment secrets and never API fields.

## Compatibility

Existing v0.2 text/OCR, embedding, RAG, Chat, and Agent contracts remain valid. New document media
types and richer citations are additive. Re-indexing is explicit because v0.2 vectors were process-local.

## Failure model

Milvus is written before Elasticsearch. If indexing fails, the service removes the affected scoped
document vectors and reports ingestion failure. Deletes address the exact tenant/base/document scope in
both stores. Reconciliation and outbox-driven indexing remain follow-up hardening before stable release.
