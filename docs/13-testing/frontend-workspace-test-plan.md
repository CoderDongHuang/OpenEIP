# Operational Frontend Workspace Test and Benchmark Plan

## Automated Coverage

- API client: envelopes, multipart handling, downloads, 204 responses, stable errors, and auth.
- View logic: registration, navigation, empty/loading/error states, status labels, and permissions.
- SSE: Chat and Agent frame validation, sequence enforcement, terminal events, and malformed input.
- Java ingestion: authorization, media routing, strict upstream envelopes, lifecycle transitions,
  stable failures, timeout behavior, and no secret/content logging.

## Integration

An isolated Compose run must complete registration, upload, knowledge creation, attachment,
processing to `READY`, streaming Chat with citations, and constrained Agent execution exclusively
through public browser-facing APIs.

## Benchmark

- Every emitted JavaScript asset remains at or below 150 KiB gzip and every feature route chunk
  remains at or below 75 KiB gzip.
- Deterministic 1 KiB text ingestion P99 stays below 500 ms on the reference environment.
- UI interaction commands do not introduce duplicate requests under rapid submission.

## Security

Verify no internal token appears in frontend sources, browser requests, built assets, logs, or public
errors. Run dependency audit and repository/runtime Trivy gates with zero HIGH/CRITICAL findings.
