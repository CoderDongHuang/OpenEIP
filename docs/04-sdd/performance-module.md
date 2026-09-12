# Performance Module SDD

## Scope

v0.9.0-alpha introduces a reproducible HTTP performance baseline harness. It
measures an explicitly supplied service endpoint with bounded GET requests and
reports latency percentiles, throughput, status codes, and transport outcomes.
The slice does not claim production capacity, high availability, automatic
scaling, or resilience under node failure.

## Design

`benchmark/run_benchmark.py` uses only the Python 3.12 standard library. A
thread pool provides bounded concurrency. DNS is resolved once before the run
and the resulting address is pinned to the TCP/TLS connection, preventing a
second resolution from being redirected to a different network. Each request
has an explicit timeout and reads in bounded chunks up to
`maxResponseBytes + 1`, so an unexpectedly large body is classified without
being persisted. Response bodies, headers, credentials, and payloads are never
included in evidence. Automatic redirects are disabled, so 3xx responses are
recorded as failures rather than silently changing the target.

URL syntax, port ranges, DNS resolution, and the complete resolved address set
are validated before requests start. The selected resolved address is pinned
for the run to prevent DNS rebinding; an empty resolution result is rejected.
The bounded reader truncates over-reading adapters at the sentinel byte so the
response-size limit also holds for non-conforming test transports.

The target URL is mandatory, limited to HTTP(S), and cannot contain user
credentials or fragments. DNS-resolved private, loopback, link-local,
reserved, and multicast addresses are rejected unless the operator explicitly
passes `--allow-private-network` for a local fixture. Requests, concurrency,
timeouts, warmups, and response size all have hard upper/lower bounds.

## Result Contract

The JSON result has `schemaVersion`, request configuration, a redacted target
(`scheme` and `host` only), `sampleCount`, elapsed time, throughput,
`latencyMs.p50/p95/p99/min/max`, `outcomes`, `statusCodes`, `errorCount`, and a
`result` of `PASS` only when every measured request returned 2xx and stayed
within the response limit. When `--max-p99-ms` is supplied, the result also
requires P99 to be at or below that threshold. Percentiles use nearest-rank over all completed
requests, including errors, to prevent failed requests from disappearing from
the latency distribution.

The versioned machine-readable contract is maintained at
`contracts/performance/performance-benchmark-result.v1.schema.json`; the API
design decision and non-applicability record are in
`docs/06-api/performance-benchmark.md`.

## Operational Boundaries

The shell wrapper requires the target URL as its first argument. Operators must
run against a non-production fixture unless a change review explicitly approves
the target. Benchmark output is evidence for regression comparison and release
review; it is not a substitute for multi-node, database, provider, chaos, or
capacity testing. Those capabilities remain outside the v0.9.0-alpha scope.

The repository includes `benchmark/local_fixture.py` for deterministic local
health responses; it binds loopback by default and is intended only for the
documented baseline procedure.
