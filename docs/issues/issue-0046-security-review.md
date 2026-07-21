# Security Review: Issue #46 Document Parsing

> Status: Passed
> Last updated: 2026-07-22

| Threat | Control | Verification | Result |
|---|---|---|---|
| Unauthorized internal call | Shared fail-closed token and canonical tenant/user/document UUID checks run before body parse | API tests reject bad token and document identity before decoding | Passed |
| Parser bomb/resource exhaustion | Stream and decoder 2 MiB cap, bounded chunk size/overlap, 10,000 chunk cap | Body-limit, invalid configuration, max-chunk and 1 MiB tests | Passed |
| Encoding/binary confusion | Strict UTF-8, BOM/control rejection, UTF-8-only text media parameter, no extension inference | Parameterized malformed/BOM/NUL/charset/media tests | Passed |
| JSON ambiguity/smuggling | Duplicate-key rejection plus strict Pydantic OCR v1 model with unknown fields forbidden | Nested parser and extra/duplicate field tests | Passed |
| Traceability corruption | Chunks are exact normalized slices with ranges, sequence, page, hash, deterministic ID | Service tests recalculate slices/hashes and repeat results | Passed |
| Persistent prompt/markup injection | Source and chunks are explicitly untrusted data; no rendering, execution, or Prompt role field | PRD/Sub-SDD constraints and API shape review | Passed |
| Event replay | Tenant/document/source/parser-bound deterministic idempotency key; event contains the key | Repeat/tenant-scope tests and event Schema Contract | Passed |
| Content/secret disclosure | No source/chunk text in event; logs absent; API excludes credential and tenant identity | API assertions and Trivy secret scan | Passed |
| Vulnerable dependencies/runtime | No new runtime dependency; pinned Debian 13.6 image and full repository/runtime scans | pip-audit and Trivy 0.72 report 0 HIGH/CRITICAL | Passed |

## Residual Risk

- The stateless module derives an idempotency key but does not persist consumed keys. Durable replay
  protection belongs to the later event consumer/outbox implementation.
- `document.lifecycle.parsed` is a contract only. Kafka delivery, retries, DLQ and outbox publication are
  not implemented by #46.
- PDF/Office/native parsers are absent. Adding them introduces separate parser-bomb and supply-chain
  surfaces requiring new test corpora and limits.
- Parsed text can contain malicious instructions or markup. Downstream RAG/Chat/Agent modules must keep
  it in a quoted data boundary and apply output/content controls.
- Static internal Token and direct `/ai/` ingress risks recorded by OCR remain until workload identity
  and production ingress policy are implemented.

## Conclusion

No blocking parser resource, encoding, JSON ambiguity, traceability, event-content, secret, or
HIGH/CRITICAL runtime risk remains inside the #46 MVP subset.

