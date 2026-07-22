# Issue #51: Constrained Agent Runtime and SPI

## Scope

Deliver Agent SPI v1, an authenticated Java/Python execution boundary, explicit tool authority,
execution-local working memory, sanitized SSE events, and bounded Document/Search tools.

## Acceptance

- Unknown or unauthorized tools never execute.
- Runtime-owned steps, deadlines, repeat detection, argument/result limits, and cancellation terminate
  every path.
- Java authorizes knowledge scope before Python and RAG receive canonical identities.
- No thought, prompt, credential, raw tool argument, or traceback reaches events or logs.
- RFC, ADR, API/SPI, threat model, tests, benchmark, security review, and six gates pass.
