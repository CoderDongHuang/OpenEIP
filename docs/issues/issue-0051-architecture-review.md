# Architecture Review: Issue #51 Agent

> Decision: Approved for implementation | Date: 2026-07-22

| Check | Decision |
|---|---|
| RFC | RFC-0003 accepts Agent SPI v1 and the constrained v0.2 boundary |
| ADR | ADR-0007 accepts finite execution and execution-local memory |
| SAD | Java retains auth/resource policy; Python owns Agent runtime and AI tool orchestration |
| SDD | runtime is a bounded single Agent; Planner/Reflection/Multi-Agent remain v0.6 |
| SPI | immutable typed `1.0` contract with explicit ToolExecutor authority |
| MCP | official SDK lifecycle only; explicit mappings; no remote transport or delegated auth |
| Security | allowlist, strict schemas, timeouts, repeat detection, bounded output, sanitized events |
| Compatibility | additive API/module/SPI; no database, existing API, SDK, or event change |

The design is approved for implementation. Any generic network/file/shell tool, persistent memory,
third-party wheel loading, remote MCP transport, or Agent SPI v2 requires a new review.
