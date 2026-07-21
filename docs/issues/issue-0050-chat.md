# Issue #50: SSE Streaming Chat

## Scope

Deliver user-owned chat sessions, ordered complete history, a JWT-protected Java SSE gateway, a
bounded internal Python RAG stream, browser cancellation, and an accessible streaming workspace.

## Acceptance

- Session and history access cannot cross user or tenant boundaries.
- Streams emit only `token`, `done`, and `error` with stable identifiers and safe JSON data.
- Disconnect cancels upstream work; no retry occurs after the first token.
- Tests, benchmark, security review, six gates, API/UI docs, and migrations pass.
