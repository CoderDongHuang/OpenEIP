# Document Parsing Module Design (Sub-SDD)

> Version: 2.0 | Date: 2026-07-24 | Status: Implemented for v0.3
> Issue: [#46](https://github.com/CoderDongHuang/OpenEIP/issues/46)

## 1. Responsibilities and Boundaries

`engine_core.parsing` validates and normalizes UTF-8 plain text or an `ocr-result.v1` document, creates
bounded traceable chunks, and produces a deterministic parsed-result contract. It does not read object
storage, authorize a file, persist knowledge records, generate embeddings, or publish Kafka messages.

The parser/chunker ports are internal Python boundaries, not public Plugin SPIs. v0.3 adds bounded PDF,
DOCX, PPTX, and XLSX adapters with page/slide/sheet attribution. Semantic chunking, complex table/layout
reconstruction, macros, encrypted files, and handwritten OCR remain out of scope.

## 2. API and Input Contract

The source contract is [document-parsing-v1.openapi.yaml](../06-api/document-parsing-v1.openapi.yaml).

| Method | Path | Content type | Result |
|---|---|---|---|
| POST | `/api/v1/parsing/documents` | `text/plain; charset=utf-8` | Normalize and chunk one UTF-8 document |
| POST | `/api/v1/parsing/documents` | `application/vnd.openeip.ocr-result.v1+json` | Validate, flatten, and chunk one OCR result |

The internal token, tenant UUID, user UUID, document UUID, and request ID are headers and are never
accepted from document content. The raw input limit is 2 MiB. JSON is parsed with duplicate-key
rejection and must conform to [`ocr-result.v1`](../../contracts/ocr/ocr-result.v1.schema.json).

## 3. Normalization and Chunking

- UTF-8 is decoded strictly; BOM, NUL, disallowed control characters, binary-looking data, and malformed
  input are rejected.
- CRLF/CR becomes LF, Unicode is normalized to NFC, line-end whitespace is removed, and outer blank
  lines are trimmed.
- The default chunk size is 1,000 characters with 100-character overlap. A boundary prefers paragraph,
  line, sentence punctuation, then whitespace, but never exceeds the configured maximum.
- Chunk count is capped at 10,000. Every chunk records zero-based `[startChar, endChar)`, sequence,
  contributing page numbers, text SHA-256, and a deterministic chunk ID.
- OCR blocks are flattened in declared order. Page attribution is computed from each block's character
  span; OCR text remains untrusted data.

## 4. Result and Event Contracts

[`document-parsed-result.v1.schema.json`](../../contracts/document/document-parsed-result.v1.schema.json)
is the full synchronous result. `normalizedTextSha256` identifies the normalized source; chunk IDs are
derived from document ID, normalized hash, parser version, index, range, and chunk hash.

[`document.lifecycle.parsed.v1.schema.json`](../../contracts/events/document.lifecycle.parsed.v1.schema.json)
contains identifiers, hashes, counts, parser version, and `idempotencyKey`, but no raw text. This module
defines the event payload only. Durable publication requires the later pipeline/outbox adapter and must
not be claimed by the parser.

## 5. Security and Reliability

- Shared internal authentication and canonical identity validation run before body parsing.
- Input bytes, normalized text, OCR text, and chunks are never logged.
- Parser output is data, not an instruction channel. Downstream LLM prompts must use an isolated context
  boundary and treat embedded HTML/Markdown/Prompt text as untrusted.
- Deterministic hashes and idempotency keys make repeated input observable without persisting replay
  state in this stateless module.

## 6. Tests and Gates

- Unit: strict UTF-8, controls/BOM/binary detection, normalization, preferred boundaries, overlap,
  page mapping, max bytes/chunks, deterministic IDs/hashes.
- Integration: both media types, internal authentication, stable errors, and response envelope.
- Contract: OpenAPI, parsed-result Schema, OCR input compatibility, and parsed-event Schema.
- Benchmark: 1 MiB normalized text through validation/chunking; P99 below 250 ms on the recorded machine.
- Coverage: total Python instruction coverage at least 80%.

## 7. Compatibility

This module adds a Python API and two Schemas. It changes no Auth/File Upload/OCR API, database
migration, SDK, or public Plugin SPI. Existing OCR results remain valid input without transformation.
