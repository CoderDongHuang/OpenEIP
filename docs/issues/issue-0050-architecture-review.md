# Architecture Review: Issue #50 Chat

> Decision: Approved for implementation | Date: 2026-07-22

| Check | Decision |
|---|---|
| SAD boundary | Java owns JWT/session/knowledge authorization; Python owns RAG generation |
| SDD streaming | SSE headers, cancellation, and no-retry-after-token follow the accepted baseline |
| Data ownership | Java MySQL stores complete sessions/messages; Python remains stateless |
| Security | browser never receives internal credential; owner and membership checks precede stream |
| Compatibility | additive API, module, tables, and UI; no existing event or Plugin SPI change |
| Scope | no tool call, planner, long-term memory, or chain-of-thought exposure |

RFC and ADR are not required because this implements the existing SAD/SDD and Spike-005 decision.
The review permits coding. Tool execution and Agent lifecycle remain exclusively in Issue #51.
