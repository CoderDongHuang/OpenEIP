# Chat Workspace UI Design

## Layout

- Header: OpenEIP identity, current user, and logout command.
- Narrow session setup rail: knowledge-base UUID, title, and new-session action.
- Main transcript: ordered user/assistant messages and verified citation metadata.
- Composer: bounded multiline input, send icon command, and cancel command while streaming.

The first screen is the usable login or chat workspace, not a marketing page. Desktop uses a fixed
280-pixel setup rail and fluid transcript. Mobile stacks setup above the transcript and keeps the
composer visible without covering messages.

## States

Login has idle, submitting, and credential-error states. Chat has no-session, loading-history,
empty-history, ready, streaming, cancelled, and failed states. Only one stream may update a session at
a time. Logout aborts the active stream and clears tokens from memory and storage.

## Accessibility and Security

Inputs have visible labels, status updates use `aria-live`, and all commands are keyboard reachable.
Stream tokens are appended as React text nodes. No message or citation content is inserted as HTML.
Bearer tokens are sent only to same-origin Java APIs and never to Python internal endpoints.
