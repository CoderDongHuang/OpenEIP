# RFC-0011: Reproducible Performance Evidence

## Status

Proposed; implementation-ready local slice. Scope: v0.9 Performance baseline.

## Abstract

Provide a dependency-free, bounded HTTP GET harness that produces redacted,
machine-readable performance evidence for an explicitly supplied endpoint.

## Motivation

Performance claims need repeatable evidence without adding production runtime
dependencies or allowing an operator to accidentally benchmark a private
production address. Existing domain benchmarks do not exercise the HTTP
control-plane boundary used by deployed services.

## Design

`benchmark/run_benchmark.py` uses Python 3.12 standard-library networking and a
bounded `ThreadPoolExecutor`. DNS is resolved once, the selected address is
pinned for every request, redirects are disabled, and response reads stop at
`maxResponseBytes + 1`. URL credentials/fragments, malformed ports, unresolved
hosts, and unapproved private/link-local/reserved/multicast targets are
rejected. The result contains aggregate latency percentiles, throughput, status
codes, outcome classes, configuration, environment, and only a redacted target
scheme/host. A P99 threshold can make the result fail.

The CLI/JSON contract and non-applicability decision are documented in
`docs/06-api/performance-benchmark.md`; the JSON Schema is under
`contracts/performance/`.

## Alternatives Considered

| Alternative | Benefit | Reason not selected |
|---|---|---|
| Locust/k6/JMeter runtime dependency | Rich load models | Adds supply-chain/runtime requirements and exceeds the v0.9 control-plane scope |
| `urllib` with default DNS/redirect behavior | Small implementation | Does not provide DNS rebinding or redirect boundary guarantees |
| Prometheus-only metrics | Integrates with operations | Does not produce a self-contained reproducible request sample |
| Full HA/chaos/capacity suite | Production confidence | Requires deployment topology and infrastructure outside this alpha |

## Impact

- **API:** No REST endpoint; an additive CLI and versioned JSON evidence contract.
- **SDK:** No SDK surface or signature change.
- **Plugin SPI:** No SPI or plugin lifecycle change.
- **Database:** No tables, migrations, or data ownership.
- **Events:** No topic or event contract.
- **Security:** Private-network opt-in, DNS pinning, no redirects, bounded reads,
  redacted evidence, and explicit non-production boundary.
- **Operations:** Operators must supply the endpoint and review the result with
  commit, service version, hardware, fixture, and timestamp metadata.

## Migration Plan

There is no runtime or data migration. Existing module APIs and deployment
services are unchanged. Consumers of the JSON result must pin `schemaVersion`
and reject incompatible versions rather than relying on undocumented fields.

## Decision

The repository implementation follows this proposal and is ready for
maintainer discussion. RFC acceptance still requires the documented discussion
period and maintainer vote; implementation evidence must not be treated as that
external decision.

## Discussion Record

The repository records the implementation and its local evidence. The required
one-week public discussion and the maintainer vote (at least two-thirds
approval) remain external governance actions and are intentionally not claimed
as complete by this document.

## References

- [Performance SDD](../04-sdd/performance-module.md)
- [Performance Benchmark Contract](../06-api/performance-benchmark.md)
- [Performance Test Plan](../13-testing/v0.9-performance-test-plan.md)
- [ADR-0020](../12-adr/adr-0020-bounded-http-benchmark.md)
