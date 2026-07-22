# Architecture Review: Issue #46 Document Parsing

> Review date: 2026-07-22
> Reviewed: Parsing Sub-SDD 1.0, OpenAPI 1.0, Parsed Result/Event Schemas 1.0
> Result: Approved for Implementation

| Check | Result | Evidence |
|---|---|---|
| SAD boundary | Passed | Python owns stateless parsing; Java retains file authorization, metadata, and transactions |
| API First | Passed | One content-negotiated internal endpoint and stable DOC errors are defined |
| Input compatibility | Passed | OCR input uses the existing language-neutral `ocr-result.v1` contract |
| Traceability | Passed | Chunks preserve normalized ranges, pages, order, hashes, and deterministic IDs |
| Event boundary | Passed | v1 event has replay key and metadata only; no fabricated publication claim |
| Security | Passed for coding | Auth before parse, strict UTF-8, duplicate-key rejection, hard byte/chunk limits, untrusted-text boundary |
| Compatibility | Passed | Additive Python API/Schemas; existing APIs, DB, SDK, and SPI unchanged |
| RFC / ADR | Not required | Implements accepted SAD parser/event boundaries and introduces no public SPI or storage decision |

## Coding Constraints

- Do not infer format from a filename or extension.
- Reject invalid encoding and controls instead of silently replacing bytes.
- Never log or execute source/chunk text.
- Every returned chunk must be a verifiable slice of normalized text with a bounded count.
- Keep security, benchmark, coverage, and Quality Gates pending until measured.

