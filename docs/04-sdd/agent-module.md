# Agent Module Design (Sub-SDD)

> Version: 1.0 | Date: 2026-07-22 | Status: Approved for Implementation
> Issue: [#51](https://github.com/CoderDongHuang/OpenEIP/issues/51) | RFC: [RFC-0003](../11-rfc/rfc-0003-agent-spi-v1.md)

## 1. Responsibilities and Boundaries

Agent owns the versioned Python SPI, deterministic single-Agent runtime, tool registry, execution-local
working memory, and sanitized SSE events. Java owns the external JWT boundary, canonical identity, and
knowledge-base authorization. RAG remains the only owner of vector retrieval and citation validation.
Agent does not persist conversations, reveal reasoning, access arbitrary URLs/files, or implement
Planner, Reflection, long-term memory, Multi-Agent, or remote MCP.

## 2. API and SPI

The HTTP source contract is [agent-v1.openapi.yaml](../06-api/agent-v1.openapi.yaml); the plugin contract
is [Agent SPI v1](../05-spi/agent-v1.md). The built-in Agent ID is `openeip.constrained-v1`. Every
execution requires a non-empty explicit `allowedTools` list. Public calls go to Java; Python endpoints
require the internal deployment credential and canonical tenant/user/execution/request headers.

SSE uses exactly `execution.started`, `tool.started`, `tool.completed`, `answer.delta`,
`execution.completed`, and `execution.error`. All events carry request and execution IDs. Tool events
contain names, stable call IDs, sequence, duration, and bounded summaries, never arguments containing
document text, prompts, credentials, or tracebacks.

## 3. Runtime State Machine

```
validate request and allowlist
  -> emit execution.started
  -> AgentSpi.execute(context)
       -> provider decision
       -> validate tool name + exact JSON arguments
       -> invoke with timeout
       -> store bounded observation in execution-local memory
       -> stop on final answer or bounded termination
  -> emit sanitized tool events
  -> emit bounded answer.delta events
  -> emit execution.completed
```

Unknown tools, undeclared tools, repeated identical calls, step exhaustion, total timeout, tool
timeout, invalid/non-finite/oversized arguments, and oversized results fail closed with `AGENT-S-001`.
Client cancellation propagates to the Agent task and tool coroutine. There is no automatic retry.

## 4. Built-in Tools

| Tool | Input | Authority | Result |
|---|---|---|---|
| `document.inspect` | bounded UTF-8 `text` | explicit request allowlist | SHA-256 plus line/word/character counts |
| `knowledge.search` | bounded `query`, `topK` | allowlist + Java membership check | bounded RAG answer and verified citation IDs |

Both schemas reject unknown properties. Neither accepts URLs, hosts, paths, shell fragments, or raw
credentials. RAG text is treated as untrusted context under the existing prompt and citation controls.

## 5. MCP Adapter

The optional adapter uses MCP SDK 1.28.1's `ClientSession` lifecycle: enter transport/session,
`initialize()`, `list_tools()`, validate an explicit local mapping, and `call_tool()`. Discovery alone
never grants authority. v0.2 does not enable remote Streamable HTTP, pass user tokens to MCP servers,
or register arbitrary discovered tools.

## 6. Limits and Quality

Input is at most 4,000 characters; body 32 KiB; steps 1-8; total duration 10 seconds; per-tool timeout
2 seconds; tool arguments 8 KiB; tool result 8 KiB; answer 8,000 characters; answer chunks 1,024;
one identical call per execution. Benchmark gates are first event P99 below 50 ms, deterministic tool
overhead P99 below 100 ms, completion P99 below 500 ms, and bounded loop termination below 100 ms.

Tests cover SPI compatibility, allowlist authority, authorization recheck, strict JSON, Prompt
Injection, unknown/repeated tools, timeouts, cancellation, result limits, event leakage, MCP lifecycle,
runtime/source contracts, and benchmark thresholds.
