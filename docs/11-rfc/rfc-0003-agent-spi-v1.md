# RFC-0003: Constrained Agent SPI v1

## Status

Accepted (Bootstrap Maintainer)

## Abstract

Define the first public Python `AgentSpi` and a bounded single-Agent runtime that exposes only
allowlisted Document and Search tools through an authenticated Java-to-Python SSE boundary.

## Motivation

The SDD contains an Agent SPI sketch but no versioned package contract, lifecycle, execution limits,
or compatibility policy. Implementing Agent plugins against that sketch would make tool authority,
memory ownership, and cancellation behavior plugin-specific. Issue #51 needs one interoperable MVP
contract before third-party Agent code exists.

## Design

- `AgentSpi` v1 is a Python abstract base class with `get_metadata()`, `get_tools()`, and async
  `execute(context) -> AgentResult` operations.
- Metadata, tool definitions, execution context, tool calls, observations, and results are immutable
  typed models. `spi_version` is exactly `1.0`.
- Java exposes the user API, authenticates JWTs, resolves canonical identities, and rechecks knowledge-
  base membership when `knowledge.search` is allowlisted. Python is never directly browser-accessible.
- Each request supplies an explicit tool allowlist. The runtime intersects it with the Agent's declared
  tools and rejects unknown or model-generated tool names before invocation.
- The built-in constrained Agent supports `document.inspect` over supplied text and
  `knowledge.search` over the already-authorized RAG scope. Neither tool accepts a URL or filesystem
  path.
- Execution has configurable maximum steps, total duration, per-tool timeout, argument size, result
  size, and duplicate-call limits. A deterministic plan provider is the offline default.
- SSE exposes lifecycle, sanitized tool, answer, completion, and stable error events. Private model
  reasoning, prompts, credentials, raw exceptions, and working memory are never events.
- Working memory is an in-process dictionary scoped to one execution and discarded at completion.
  Persistent/long-term memory, Planner, Reflection, Multi-Agent, and remote MCP authentication remain
  outside v0.2.
- The optional MCP adapter uses the official Python SDK lifecycle (`initialize`, `list_tools`, and
  `call_tool`). Only explicitly mapped tools enter the runtime allowlist; remote transport is disabled.

## Alternatives Considered

| Option | Benefit | Cost | Decision |
|---|---|---|---|
| Expose Python directly | Less Java code | Bypasses the accepted authorization boundary | Rejected |
| Let plugins call arbitrary functions | Maximum flexibility | No central timeout, audit, or authority control | Rejected |
| Persist every reasoning step | Debug visibility | Leaks thought/prompt data and expands retention risk | Rejected |
| Adopt a full graph/planner framework now | More orchestration features | Premature v0.6 scope and dependency lock-in | Rejected |
| Custom MCP JSON-RPC adapter | Small implementation | Not protocol-compatible; contradicts Spike-004 | Rejected |

## Impact

- API: adds `/api/v1/agents` and `/api/v1/agents/{agentId}/executions:stream` plus internal Python
  equivalents.
- SDK: no generated client is shipped in v0.2.
- Plugin SPI: introduces Python `AgentSpi` version `1.0`; future incompatible changes require `2.0`.
- Database: no migration; v0.2 working memory is execution-local.
- Security: adds a tool authority boundary, bounded execution loop, stable audit events, and Java-side
  resource authorization.

## Migration Plan

There is no predecessor SPI. Built-in Agents declare `spi_version=1.0`. Additive optional fields may
be introduced in v1 with defaults; removed fields, changed semantics, or wider tool authority require
a new major SPI and an adapter period.

## References

- Issue #51
- SAD sections 6.3 and 6.4
- SDD sections 6.2 and 7
- Spike-004 MCP Runtime validation

## Decision Record

The Bootstrap Maintainer accepts this contract under the temporary pre-community governance used by
RFC-0001 and RFC-0002. Public review remains required before this Draft PR is merged.
