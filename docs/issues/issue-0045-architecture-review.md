# Architecture Review: Issue #45 OCR

> Review date: 2026-07-22
> Reviewed: OCR Sub-SDD 1.0, OpenAPI 1.0, OCR Result Schema 1.0
> Result: Approved for Implementation

| Check | Result | Evidence |
|---|---|---|
| SAD boundary | Passed | Python owns AI execution; Java remains owner of authorization transactions and file metadata |
| API First | Passed | One versioned raw-image operation and stable error contracts are defined |
| Data ownership | Passed | OCR is stateless and creates no Java transaction or database record |
| Provider boundary | Passed | Internal port is replaceable but is not presented as a public Plugin SPI |
| Security | Passed for coding | Internal token, canonical identity, format verification, hard byte/dimension/pixel/frame limits |
| Parsing contract | Passed | Language-neutral v1 result schema excludes image bytes, secrets, and identity context |
| Compatibility | Passed | Additive Python API/schema; existing Auth/File APIs, DB, SDK, and SPI unchanged |
| RFC / ADR | Not required | No SAD boundary, public SPI, storage, or communication decision changes |

## Coding Constraints

- Never trust the declared MIME type without verifying the decoded container.
- Do not decode an image after resource limits indicate it is unsafe.
- Never log image bytes, recognized text, internal credentials, or caller identity headers.
- Treat OCR text as untrusted data in every downstream LLM prompt.
- Do not label the deterministic MVP provider as a production-accuracy OCR model.
- Keep the security review, benchmark, coverage, and six Quality Gates pending until measured.

