# Chat Module Design (Sub-SDD)

> Version: 1.0 | Date: 2026-07-22 | Status: Approved for Implementation
> Issue: [#50](https://github.com/CoderDongHuang/OpenEIP/issues/50) | SAD: [Architecture Baseline](../03-sad/zh-CN.md)

## 1. Responsibilities and Boundaries

Chat owns user-scoped sessions, complete message history, and an authenticated SSE bridge. The browser
calls Java with a JWT; Java verifies session ownership and knowledge-base membership, then calls the
Python internal stream with a deployment credential and canonical identities. Python produces bounded
RAG-backed token events. Chat does not execute tools, plan tasks, expose chain-of-thought, or implement
Agent memory.

## 2. APIs and Streaming Contract

The source contract is [chat-v1.openapi.yaml](../06-api/chat-v1.openapi.yaml). Java exposes session
creation, history, and `messages:stream`. Python exposes only the internal stream. SSE uses exactly
`token`, `done`, and `error`; each data object carries `requestId` and `sessionId`. Tokens also carry a
strictly increasing `sequence`. The stream sets `text/event-stream`, `Cache-Control: no-cache,
no-transform`, and `X-Accel-Buffering: no`.

Java generates the request ID and ignores browser-supplied internal identity headers. Browser
disconnect closes the Java emitter, input stream, and upstream future. Once any token is emitted the
gateway never retries. Errors after headers are committed become a stable `error` event.

## 3. Authorization and Persistence

`chat_sessions` stores tenant, owner, knowledge base, title, and timestamps. `chat_messages` stores
ordered `user` and `assistant` content. Every lookup includes tenant and owner before returning a
session or message. Session creation calls the Knowledge service to verify current membership. A
second membership check occurs before every stream so revoked access takes effect.

The user message is stored before generation. The assistant message is stored only after a valid
`done`; cancelled or failed partial output is not committed as a complete assistant response. Message
content is never logged. Tables are introduced by reversible Flyway migration `V2.3.0`.

## 4. Python Generation

The Python Chat service calls the already-scoped RAG service and emits bounded UTF-8 text chunks. The
deterministic provider proves the full stream and cancellation mechanics offline; it is not a model
quality claim. RAG citations are emitted only in `done` after the RAG allowlist validation already
passes. Provider exceptions map to a stable error without prompt, vector, context, key, or traceback.

## 5. Frontend

The MVP workspace provides login, knowledge-base selection by ID, session creation, accessible message
history, streamed assistant output, cancel, retry as a new user action, loading/empty/error states, and
logout. The client parses SSE incrementally across arbitrary network chunk boundaries and treats event
data as text, never HTML.

## 6. Limits and Quality

Request bodies are capped at 32 KiB; messages at 4,000 characters; titles at 120; sessions at 100,000
messages; token chunks at 1,024 characters; generated answers at 8,000. Benchmark gates are first token
P99 below 100 ms, completion P99 below 500 ms, and 20 concurrent deterministic streams without error.
Tests cover IDOR, membership revocation, SSE injection, fragmented parsing, cancellation, persistence,
provider failure, body limits, and source/runtime contracts.
