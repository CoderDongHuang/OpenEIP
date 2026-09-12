# Performance Benchmark Contract v1

## Surface Decision

The v0.9 Performance module has no REST endpoint, database table, event topic,
frontend view, SDK method, or Plugin SPI. It is an operator-invoked CLI under
`benchmark/` and emits an aggregate JSON evidence document. The explicit
non-applicability decision prevents the harness from becoming a runtime
dependency or an accidental public load endpoint.

## Input Contract

The Python entry point requires `--target-url` and accepts bounded request
configuration: `--requests` (1..100,000), `--concurrency` (1..256),
`--timeout` (0.001..300 seconds), `--warmups` (0..1,000), and
`--max-response-bytes` (1..16 MiB). HTTP and HTTPS are supported. Credentials
and fragments are rejected, redirects are never followed, and resolved
private, loopback, link-local, reserved, or multicast addresses require the
explicit `--allow-private-network` opt-in.

The shell entry point accepts the target URL as its first positional argument
and forwards the same options to the Python entry point.

## Output Contract

The output contains aggregate latency, throughput, status counts, outcome
counts, error count, redacted target metadata, configuration, runtime
environment, and `result`. It never contains response bodies, headers, request
payloads, credentials, or full target URLs. `result` is `PASS` only when every
measured request is a 2xx response within the body limit and, when supplied,
the nearest-rank P99 is within `--max-p99-ms`.

The machine-readable contract is
[the Performance Benchmark Result v1 Schema][performance-schema].

## Compatibility

This is an additive control-plane artifact. No existing API, database,
event, SDK, or Plugin SPI changes. Result fields may be added only in a new
schema version; existing fields cannot be renamed, removed, or retyped within
v1.

[performance-schema]: ../../contracts/performance/performance-benchmark-result.v1.schema.json
