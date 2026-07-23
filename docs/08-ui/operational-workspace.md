# Operational Workspace UI Design

## Navigation

The authenticated shell uses a compact persistent sidebar on desktop and a drawer on mobile. Primary
destinations are Overview, Documents, Knowledge, Chat, Agent, and Access. The product identity and
current user stay visible without consuming a marketing-style hero area.

## Views

- Overview: resource counts, processing health, and direct next actions.
- Documents: upload command, scan-friendly metadata table, download, and confirmed deletion.
- Knowledge: master/detail layout, create/edit commands, attached documents, status, and processing.
- Chat: knowledge-base selector, session title, transcript, citations, composer, cancel, and retry.
- Agent: catalog selector, tool checkboxes, bounded step control, input, run/cancel, and event timeline.
- Access: current identity; registration is available from the unauthenticated screen. Admin-only role
  controls render only when the authenticated permissions allow them.

## Responsive and Accessibility

Tables collapse to metadata lists below 768 px. Commands use icons with tooltips. Inputs have visible
labels, streaming/status regions use `aria-live`, keyboard navigation is preserved, and fixed controls
have stable dimensions. Destructive operations require a named confirmation dialog.

## Security

No HTML injection API is used. Tokens remain in session storage and are sent only to same-origin Java
paths. Internal service headers, raw Agent tool arguments, provider errors, and document bodies are
never stored in browser state beyond the immediate upload object.
