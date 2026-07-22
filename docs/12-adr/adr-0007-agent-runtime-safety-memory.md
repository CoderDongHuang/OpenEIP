# ADR-0007: Bound Agent Execution and Keep Memory Ephemeral

## Status

Accepted

## Date

2026-07-22

## Context

Agent execution combines untrusted user input, model-selected tools, retrieved content, and internal
credentials. An unconstrained ReAct loop can repeat calls, invoke undeclared capabilities, retain
sensitive observations, or expose private reasoning. The v0.2 MVP needs useful tool execution without
committing to the v0.6 Planner, long-term memory, Multi-Agent, and evaluation architecture.

## Decision

Use a finite single-Agent state machine. Java authenticates and authorizes the requested resource
scope before Python receives canonical identities. Python validates an explicit per-request tool
allowlist, enforces maximum steps, total and per-tool timeouts, repeated-call detection, strict JSON
arguments, and bounded results. Only sanitized lifecycle and tool outcome events are externally
observable. Working memory is a dictionary owned by one execution and is discarded on every terminal
path. No chain-of-thought or provider prompt is persisted or emitted.

MCP integration uses the official Python SDK lifecycle and remains local stdio plus explicit mappings
in v0.2. Remote MCP transport, credentials, tenant delegation, and persistent memory require a later
RFC and threat review.

## Consequences

### Positive

- Tool authority is explicit and independently enforceable from model output.
- Loop, latency, and memory behavior are deterministic and testable offline.
- No new database or retention policy is needed for v0.2.
- Stable public events support audit and UI consumers without exposing reasoning.

### Negative

- Work cannot resume after a process failure.
- The MVP cannot learn user preferences or coordinate multiple Agents.
- The deterministic provider validates runtime mechanics, not model quality.

### Risks

- A future tool may hide network or filesystem access behind a benign name; registration therefore
  requires a separate tool security review and immutable schema.
- In-process deadlines cannot forcibly stop blocking native code; tool adapters must remain async or
  isolated, and production adapters require cancellation tests.

## Alternatives Considered

| Option | Benefit | Cost | Why not selected |
|---|---|---|---|
| Redis short-term memory | Cross-instance resume | New retention and availability dependency | Not needed for one execution |
| Persist full traces in MySQL | Durable audit | Stores sensitive prompts and observations | Stable metadata events are sufficient |
| Let the model terminate itself | Simple loop | Unbounded cost and denial-of-service risk | Runtime must own termination |
| Enable remote MCP by default | Broad tool ecosystem | Unresolved auth, tenant, SSRF, and egress controls | Deferred to v0.6 |
