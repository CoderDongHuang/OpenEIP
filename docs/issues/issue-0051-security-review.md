# Security Review: Issue #51 Agent

> Status: Passed | Date: 2026-07-22

| Threat | Control | Evidence |
|---|---|---|
| Tool authority escalation | every execution supplies a non-empty allowlist; the runtime and Java gateway reject undeclared tools | Python runtime/API tests and Java gateway transition tests |
| Knowledge-base IDOR | Java resolves the authenticated user and rechecks knowledge membership before opening the Python stream | Java service tests and isolated Compose search execution |
| Provider-controlled execution | provider decisions have no authority; exact arguments, byte limits, step limits, deadlines, and runtime-owned execution are enforced | unknown-tool, injected-argument, timeout, and termination tests |
| Event transition forgery | request/execution IDs and sequences are canonical; tool call ID, name, and step must match and cannot be reused | Java gateway lifecycle and malformed transition matrix |
| Infinite or repeated work | total/tool timeouts, maximum eight steps, repeated-call fingerprints, and no automatic retries bound every execution | repeat, exhaustion, timeout, and benchmark tests |
| Prompt or secret disclosure | public SSE contains only allowlisted exact-field events; Python errors map to stable `AGENT-S-001` | secret-bearing provider, MCP, gateway, and Compose sentinel tests |
| SSE injection | events are fixed, data is single-line JSON, text/control characters and per-event/total answer sizes are bounded | API encoder and Java strict parser tests |
| MCP discovery escalation | MCP is disabled as a built-in tool; the optional stdio adapter requires an explicit local mapping before discovery and invocation | seven lifecycle, mapping, SDK failure, and result-bound tests |
| Cancellation failure | client cancellation propagates to the Agent task and active provider/tool coroutine | Python cancellation test and Java disconnected-output test |
| Resource exhaustion | 32 KiB body, 4,000-character input, 8 KiB arguments/results, 8,000-character answer, and 10-second execution limit | request, runtime, tool, and gateway boundary tests |

## Residual Risk

- The deterministic provider validates authority, transport, event integrity, and termination. It does
  not establish production-model quality, Prompt Injection resistance, or external-model latency.
- Java and Python currently share one deployment credential. Production promotion requires managed
  secret rotation, service identity, TLS, and network policy around the internal endpoint.
- Knowledge membership is checked immediately before execution. A future execution duration above the
  current 10-second bound would require revocation-aware cancellation or a shorter authorization lease.
- Enabling MCP servers adds a new code and data trust boundary. Remote transport, delegated user
  credentials, dynamic tool registration, and third-party packages remain prohibited in v0.2.

Trivy 0.72.0 found zero HIGH/CRITICAL vulnerabilities in the 514-file Git-relevant source snapshot,
the Java bootJar's 87 runtime libraries, and the Debian 13.6/Python runtime image. Repository secret
and misconfiguration scans also returned zero findings. `pip-audit` and Frontend `npm audit` found no
known vulnerabilities; the Docusaurus development chain retains 18 Moderate findings with no
available upstream fix and no HIGH/CRITICAL finding.
