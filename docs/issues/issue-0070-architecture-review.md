# Architecture Review: Issue #70 v0.4 Workflow

## Result

Approved for implementation against RFC-0006 and ADR-0010.

- Java is the only public Workflow authorization and state-transition boundary.
- MySQL is authoritative; state, ordered history, and outbox records commit together.
- Execution is at least once around ambiguous external calls and uses stable invocation IDs. No
  exactly-once side-effect claim is permitted.
- The graph compiler rejects arbitrary cycles and statically enforces node, edge, fan-out, loop, timeout,
  output, event, and retry limits.
- Webhook secrets are random, displayed once, stored as hashes, rate-limited, and never emitted.
- Manual, webhook, cron, and Kafka triggers share one idempotent execution command.
- Approval decisions use conditional single-winner updates and explicit assignee authorization.
- SSE and Kafka events are ordered/sanitized projections, not alternate sources of truth.
- The browser edits drafts only. All validation, publication, execution, retry, and approval decisions
  are repeated server-side.

## Implementation Conditions

The implementation PR must include MySQL migration tests, single-node restart continuation, trigger and approval
race tests, strict OpenAPI/event contract checks, bounded output tests, outbox delivery/deduplication,
public-gateway smoke, and an interactive Canvas verified at desktop and mobile viewports.

Dynamic Workflow plugins, arbitrary code, recursive sub-workflows, distributed consensus, and BPMN
compatibility remain excluded. Adding any of them requires a separate RFC and security review.
