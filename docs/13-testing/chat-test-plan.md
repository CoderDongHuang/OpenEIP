# Chat Test Plan

> Issue: #50 | Gates: coverage >= 80%, first-token P99 < 100 ms, completion P99 < 500 ms

| Layer | Coverage |
|---|---|
| Java unit | session ownership, membership recheck, history order, complete-only assistant persistence |
| Java integration | migration/rollback, JWT boundary, IDOR 404, SSE headers/events, cancellation |
| Python unit | strict request, bounded tokens, stable event JSON, cancellation, provider/RAG failure |
| Frontend | fragmented SSE parsing, event allowlist, cancel, text-only rendering, auth/session state |
| Contract | Java/Python runtime paths and source OpenAPI remain synchronized |
| Benchmark | first token, completion, 20 concurrent deterministic streams |
| Security | IDOR, Prompt Injection, SSE injection, message leakage, internal-token isolation |

The deterministic benchmark validates transport and lifecycle behavior only. It does not claim real
model latency, token throughput, answer quality, or Redis-backed horizontal session state.
