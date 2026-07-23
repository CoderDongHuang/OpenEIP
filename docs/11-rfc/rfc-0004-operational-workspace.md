# RFC-0004: Operational Frontend Workspace and Ingestion Boundary

## Status

Accepted (Bootstrap Maintainer)

## Abstract

Expose the existing v0.2 control-plane and AI capabilities through one authenticated operational
workspace, and add a Java-owned command that safely closes the document-to-RAG ingestion loop.

## Motivation

The v0.2 frontend requires users to copy a knowledge-base UUID and provides no way to upload,
attach, or process a document. Calling Python from the browser would expose the shared internal token
and bypass Java resource authorization. A public Java orchestration boundary is required before the
backend modules form a usable product workflow.

## Design

- React uses feature views for Overview, Documents, Knowledge, Chat, Agent, and Access.
- Access tokens remain in session storage; refresh tokens are not persisted by this UI version.
- Java exposes `POST .../documents/{documentId}/processing`, rechecks membership and file access,
  reads the bounded stored object, and calls Python with the deployment-managed internal token.
- Plain UTF-8 files go directly to parsing. PNG/JPEG files go through OCR and then the versioned OCR
  result parser. Parsed chunks are embedded for the selected knowledge base.
- Java advances the existing persisted processing state only after each upstream operation succeeds.
  Stable failure codes are visible; raw upstream payloads and exceptions are not.
- The command is synchronous and bounded for the single-node MVP. The browser disables duplicate
  submission and refreshes canonical status after completion.
- Chat selects a knowledge base by name instead of accepting a copied UUID. Agent executions use the
  existing public Java SSE endpoint and an explicit declared-tool allowlist.

## Alternatives Considered

| Option | Benefit | Cost | Decision |
|---|---|---|---|
| Browser calls Python | Less Java code | Exposes internal authority and permits identity forgery | Rejected |
| Add Kafka to default Compose now | Production-shaped async flow | Operational and recovery scope exceeds this UI delivery | Deferred |
| UI only, no processing command | Small frontend change | Uploaded documents can never become searchable | Rejected |
| Bounded Java orchestration | Secure usable loop with existing contracts | Request remains open during processing | Accepted for MVP |

## Impact

- API: one additive authenticated processing command.
- Database: no schema change; existing knowledge document status is authoritative.
- Runtime: Java makes bounded REST calls to Python; internal token never reaches the browser.
- UI: replaces the Chat-only shell with an operational workspace.

## References

- Issue #61
- ADR-0002 cross-runtime communication boundaries
- ADR-0008 synchronous ingestion orchestration
- File, Knowledge, OCR, Parsing, Embedding, Chat, and Agent v1 contracts
