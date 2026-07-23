# Issue #61: Operational Frontend Workspace

## Scope

Deliver a user-operable workspace for registration, document management, knowledge-base management,
bounded ingestion, streaming Chat, and constrained Agent execution. The browser uses only public
same-origin Java APIs; Python internal credentials and endpoints remain server-side.

## Acceptance

- A new user can register, sign in, and inspect the current identity.
- A user can upload, download, list, and delete owned documents.
- A user can create and manage knowledge bases, attach a document, start bounded processing, and
  observe `PENDING_PARSE`, `PARSED`, `PENDING_EMBEDDING`, `READY`, or `FAILED`.
- A ready knowledge base can be selected directly for streaming Chat with citations.
- A constrained Agent can run with an explicit tool allowlist and a visible sanitized event timeline.
- Internal service tokens, raw prompts, tool arguments, and document content are not persisted in
  browser storage or rendered as HTML.
- Responsive UI states, API docs, tests, benchmark, security review, and all six quality gates pass.
