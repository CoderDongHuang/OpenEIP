# Security Review: Issue #50 Chat

> Status: Passed | Date: 2026-07-22

| Threat | Control | Evidence |
|---|---|---|
| Session IDOR | every session/history lookup includes the authenticated owner; unauthorized IDs return 404 | controller and service IDOR tests |
| Revoked knowledge access | membership is checked at session creation and immediately before every stream | revocation integration test |
| Concurrent stream corruption | one request lease per session; completion/cancellation releases the lease | concurrent stream and cancellation tests |
| Internal API impersonation | Java supplies canonical tenant/user/session/request identities and a deployment credential | invalid token and mismatched identity tests |
| SSE injection | event names are allowlisted, frames are JSON encoded, sequences are monotonic, and token sizes are bounded | Python encoder, Java gateway, and frontend parser tests |
| Citation corruption | Java and the browser validate UUID, chunk ID, SHA-256, finite score range, exact fields, and uniqueness | malformed and duplicate citation tests |
| Partial-answer persistence | user input is committed before generation; assistant output only after a validated `done` | failure, disconnect, and completion tests |
| Prompt or secret disclosure | message content is never logged; upstream errors map to stable public codes | secret-bearing provider and gateway failure tests |
| Input exhaustion | 32 KiB body, 4,000-character message, 1,024-character token, 8,000-character answer, and 20-citation limits | API, service, and gateway limit tests |
| Browser token exposure | bearer token stays in same-origin session storage and is never sent to Python | frontend API review and isolated Compose E2E |

## Residual Risk

- The deterministic provider proves transport, authorization, persistence, and cancellation behavior;
  it does not establish production-model answer quality, safety, or latency.
- The deployment credential is shared by Java and Python. Production promotion still requires secret
  rotation, workload identity, TLS, and network policy around the internal endpoint.
- The in-process stream lease prevents duplicate work on one Java instance. Horizontal deployment
  requires a distributed lease before production scale-out.
- Browser session storage limits persistence but remains readable by same-origin JavaScript. The
  frontend must retain a strict Content Security Policy and avoid untrusted script execution.

Trivy 0.72.0 found zero HIGH/CRITICAL vulnerabilities in the Git-relevant source snapshot, the Java
fat-JAR runtime dependencies, and the Debian 13.6/Python runtime image. Repository secret and
misconfiguration scans also returned zero findings. `pip-audit` and Frontend `npm audit` found no
known vulnerabilities; the Docusaurus development chain retains 18 Moderate findings and no
HIGH/CRITICAL finding.
