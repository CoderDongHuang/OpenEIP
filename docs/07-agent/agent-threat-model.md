# Agent v1 Threat Model

| Threat | Required control |
|---|---|
| Tool privilege escalation | explicit request allowlist intersected with immutable Agent declarations |
| Unknown model tool | reject before argument decoding or invocation |
| Argument injection | exact typed schemas, unknown-field rejection, byte and character bounds |
| SSRF and file access | built-in tools accept neither URL nor path; no generic HTTP/file/shell tool |
| Prompt Injection | retrieved/tool text remains untrusted data and cannot alter runtime authority |
| Infinite or repeated loop | maximum steps, total deadline, and duplicate call fingerprint termination |
| Slow or blocking tool | per-tool timeout, cancellation propagation, no retry |
| Result/resource exhaustion | bounded body, arguments, observations, memory, answer, and SSE chunks |
| Cross-tenant search | Java membership check plus canonical tenant/base scope passed to RAG |
| Secret/thought leakage | stable errors and sanitized events; no prompts, raw arguments, thoughts, or tracebacks |
| MCP discovery escalation | discovery does not register tools; explicit local mapping and allowlist required |
| Audit injection | JSON-only event encoding, fixed event names, canonical IDs, bounded summaries |

Production promotion additionally requires workload identity, TLS, network egress policy, distributed
deadlines, provider-specific Prompt Injection evaluation, and isolation for third-party plugin code.
