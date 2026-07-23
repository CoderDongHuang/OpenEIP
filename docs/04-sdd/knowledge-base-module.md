# Knowledge Base Module Design (Sub-SDD)

> Version: 1.1 | Date: 2026-07-23 | Status: Implemented
> Issue: [#47](https://github.com/CoderDongHuang/OpenEIP/issues/47) | RFC: [RFC-0002](../11-rfc/rfc-0002-document-control-plane.md) | ADR: [ADR-0002](../12-adr/adr-0002-communication-boundaries.md)

## 1. Responsibilities and Boundaries

`platform-knowledge` owns knowledge-base metadata, membership, document association, processing state,
and durable event de-duplication. It does not upload objects, parse content, generate embeddings, query
Milvus, or publish model responses. `platform-app` composes it with Auth and Document.

## 2. Access Model

Every row contains `tenant_id`. The v0.2 runtime derives the logical tenant `default` server-side and
never accepts it from an API request. A base has exactly one `OWNER`; `EDITOR` may read, update, and
attach documents; `VIEWER` is read-only. Repository queries always include tenant and membership.
Inaccessible and absent resources both return `KNOW-E-001` to prevent UUID enumeration.

## 3. API

The source contract is [knowledge-base-v1.openapi.yaml](../06-api/knowledge-base-v1.openapi.yaml).

| Method | Path | Required role |
|---|---|---|
| POST | `/api/v1/knowledge/bases` | authenticated |
| GET | `/api/v1/knowledge/bases` | member |
| GET/PATCH/DELETE | `/api/v1/knowledge/bases/{baseId}` | member / EDITOR / OWNER |
| POST | `/api/v1/knowledge/bases/{baseId}/documents` | EDITOR |
| GET | `/api/v1/knowledge/bases/{baseId}/documents` | member |
| GET/DELETE | `/api/v1/knowledge/bases/{baseId}/documents/{documentId}` | member / EDITOR |
| POST | `/api/v1/knowledge/bases/{baseId}/documents/{documentId}/processing` | EDITOR |
| POST | `/api/v1/knowledge/bases/{baseId}/documents/{documentId}/processing/retry` | EDITOR |

Names are trimmed, 1-120 characters, and unique per tenant and owner. Descriptions are at most 2,000
characters. Lists use one-based pages and a maximum page size of 100.

## 4. Lifecycle

```text
PENDING_PARSE -> PARSED -> PENDING_EMBEDDING -> READY
      |            |              |
      +------------+--------------+-> FAILED
```

An attach starts at `PENDING_PARSE`. Parsed events advance to `PARSED`; scheduling embedding advances
to `PENDING_EMBEDDING`; embedding-completed advances to `READY`. Repeating the current state is
idempotent. Other transitions are rejected and rolled back. `FAILED` retains only a bounded diagnostic
code and retry count, never source text or credentials.

The first processing command accepts an attached document that has not reached a terminal state.
`retry` is an explicit recovery command for `FAILED` and `READY`: it resets the persisted state to
`PENDING_PARSE`, clears only the current failure code, preserves the bounded retry counter, and rebuilds
the document. Rebuilding `READY` is required in the v0.2 single-node profile because vector data is held
in Python process memory and can be lost on restart. Both commands require `OWNER` or `EDITOR`; a
`VIEWER` cannot trigger storage reads, parsing, or embedding through the API.

## 5. Events and Delivery

Parsed and embedding-completed Kafka adapters invoke one transactional service. `knowledge_processed_events`
uses `(tenant_id, event_id)` as its durable de-duplication key. The same event and payload returns the
stored outcome; an event ID reused with a different identity is rejected. Processing failures may be
retried three times before the adapter sends the sanitized envelope to the source topic's `.DLQ` on the
same partition. The listeners are disabled by default and require `KNOWLEDGE_KAFKA_ENABLED=true`.
The external fixed tenant UUID is mapped to the RFC-0002 internal logical tenant `default`; unknown
tenant UUIDs are rejected. This module consumes events but does not claim event publication or outbox
delivery, which remain producer responsibilities.

## 6. Security and Observability

- API identifiers are server-generated UUIDs and are always checked with tenant membership.
- Event type, version, tenant, document, and target state are validated before mutation.
- Error messages expose stable codes, not SQL, text, hashes, broker payloads, or membership details.
- Metrics cover base/document counts, state transitions, duplicate events, failures, and transition
  latency. Logs contain request/trace IDs and resource UUIDs only.

## 7. Quality and Compatibility

Tests cover the state machine, access matrix, processing authorization, retry/rebuild, API envelope,
MySQL constraints, rollback, event replay,
and a 1,000-transition benchmark. Instruction coverage must be at least 80% and P99 transition latency
must stay below 50 ms on the in-memory contract fixture. Changes are additive: one Gradle module, four
tables, API paths, and an event Schema; no existing API or public SPI changes.
