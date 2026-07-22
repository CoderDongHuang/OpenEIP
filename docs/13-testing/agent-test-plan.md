# Agent Test and Benchmark Plan

> Issue: #51 | Coverage gate: >= 80%

| Layer | Coverage |
|---|---|
| Python unit | SPI metadata, tools, planner decisions, working memory, limits, termination |
| Python API | strict auth/identity/JSON, body bounds, SSE lifecycle, cancellation |
| Java unit/integration | JWT, knowledge authorization, allowlist, gateway identity and event validation |
| Contract | OpenAPI runtime paths, Agent SPI shape/version, MCP SDK lifecycle |
| Security | unknown/unauthorized tool, argument injection, SSRF/path, prompt injection, loops, leakage |
| Benchmark | first event, tool overhead, completion, and bounded loop termination |

Thresholds: first event P99 < 50 ms, deterministic tool overhead P99 < 100 ms, completion P99 < 500
ms, and loop termination < 100 ms. The benchmark validates runtime mechanics only, not production
model quality or remote MCP performance.
