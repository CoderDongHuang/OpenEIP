# Constrained Agent v1

The v0.2 built-in Agent proves the Agent SPI and runtime safety boundary. It is not a general planner.

| Property | Value |
|---|---|
| Agent ID | `openeip.constrained-v1` |
| SPI | `1.0` |
| Tools | `document.inspect`, `knowledge.search` |
| Memory | execution-local working dictionary |
| Provider | deterministic offline plan fixture |
| Termination | final answer, max steps, repeat detection, timeout, cancellation, or stable failure |

The deterministic provider selects `document.inspect` for input prefixed with `inspect:` and
`knowledge.search` for input prefixed with `search:`. Other input returns a bounded direct answer.
This syntax is an offline validation fixture, not a user-facing prompt contract or model-quality claim.

Tool results are untrusted observations. They never change the runtime allowlist, limits, system
policy, or identity scope. The final answer and events contain no chain-of-thought.
