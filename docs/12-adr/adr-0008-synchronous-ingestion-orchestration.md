# ADR-0008: Synchronous Ingestion Orchestration for the Single-Node MVP

## Status

Accepted

## Date

2026-07-22

## Context

Document upload and knowledge attachment are persisted in Java, while OCR, parsing, and embedding
are internal Python capabilities. Kafka production processing is validated but intentionally absent
from the default v0.2 deployment. The operational UI needs a secure path that can make an attached
document searchable without exposing the Python service credential.

## Decision

Java owns an authenticated, bounded synchronous processing command. It authorizes the knowledge base
and file, calls the existing versioned Python REST contracts, validates strict response envelopes,
and advances the existing knowledge status machine. Calls use fixed timeouts, no automatic retry,
bounded input inherited from upload policy, and stable public failure codes.

The command is accepted only for the single-node deterministic-provider MVP. Production providers,
large files, bulk ingestion, cancellation, retry scheduling, and horizontal workers must use an
asynchronous job model and require a superseding ADR.

## Consequences

- Users can complete upload-to-RAG through public APIs and the UI.
- Java remains the only browser-facing authorization boundary.
- Processing occupies one request thread and is not horizontally durable; this is an explicit alpha
  limitation rather than a production worker design.
