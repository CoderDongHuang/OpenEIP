# Operational Frontend Workspace Module Design

> Version: 1.0 | Date: 2026-07-22 | Status: Approved for Implementation

## Responsibilities

- Present authenticated operational navigation and canonical resource state.
- Provide registration, documents, knowledge bases, processing, Chat, Agent, and access views.
- Keep browser authority below the Java API boundary and render all external text as React text.
- Provide stable loading, empty, error, destructive-confirmation, streaming, and responsive states.

## Boundaries

| Layer | Owns | Must not own |
|---|---|---|
| React | view state, form validation, same-origin calls, sanitized event presentation | resource authorization or internal credentials |
| Java | identity, RBAC, file/knowledge authorization, ingestion orchestration, stable errors | model/provider implementation |
| Python | bounded OCR, parsing, embedding, RAG, and Agent runtime | browser identity or public authorization |

## Feature Structure

- `auth`: login, registration, current identity, admin role controls when authorized.
- `documents`: upload queue, metadata table, download, delete.
- `knowledge`: base list/detail, attachment, canonical processing lifecycle.
- `chat`: base selection, session creation, history, SSE answer and citations.
- `agent`: catalog, allowlist, bounded execution form, sanitized event timeline.
- `shell`: responsive sidebar, header identity, route-level error and loading states.

## Ingestion Flow

1. User uploads a supported file and attaches it to an editable knowledge base.
2. UI invokes the Java processing command with no internal token or user-supplied identity header.
3. Java resolves the authenticated identity, rechecks access, and reads the stored file.
4. Java invokes OCR when required, then parsing and embedding through strict internal contracts.
5. Java advances status to `READY`; any bounded upstream failure becomes `FAILED` with a stable code.
6. UI reloads canonical status. Chat and `knowledge.search` are enabled only for a ready base.

## Compatibility

All changes are additive. Existing Chat-only sessions, v1 resource APIs, database schema, events, and
Agent SPI remain compatible.
