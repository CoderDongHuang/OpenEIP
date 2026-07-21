# OCR Module Design (Sub-SDD)

> Version: 1.0 | Date: 2026-07-22 | Status: Approved for Implementation
> Issue: [#45](https://github.com/CoderDongHuang/OpenEIP/issues/45)

## 1. Responsibilities and Boundaries

`engine_core.ocr` owns image validation, OCR provider orchestration, normalized OCR results, and
execution metrics. It does not own uploaded-file metadata, object storage, document lifecycle state,
or Java authorization transactions. Callers provide image bytes after enforcing their own resource
authorization.

The provider is an internal Python port, not a public Plugin SPI. Replacing it with PaddleOCR or a
remote model does not change the versioned API or result schema. A public OCR SPI would require a new
RFC.

## 2. API First

The source contract is [ocr-v1.openapi.yaml](../06-api/ocr-v1.openapi.yaml). The engine exposes:

| Method | Path | Result |
|---|---|---|
| POST | `/api/v1/ocr/recognitions` | Validate one PNG/JPEG image and return normalized OCR text and blocks |

The request body is the raw image rather than multipart data. Required headers carry an internal
service credential, tenant UUID, user UUID, and request ID. The API never trusts identity values from
the body and never returns the internal credential.

## 3. MVP Provider

The default `deterministic-raster-v1` provider performs real pixel matching for a deliberately bounded
5x7 uppercase Latin/digit alphabet. It exists to validate decoding, orchestration, block geometry,
confidence, compatibility, and repeatable benchmarks without downloading a model during build or
claiming production OCR accuracy.

- Supported input containers: PNG and JPEG.
- Supported deterministic corpus: high-contrast, upright, single-page raster text using the documented
  5x7 alphabet (`A-Z`, `0-9`, space, `-`, `.`, `/`, `:`).
- Unsupported or low-confidence glyphs are reported as `?`; they are never silently invented.
- Production language/layout accuracy, handwriting, rotation, tables, and PaddleOCR are later adapter
  work and require a model-specific benchmark.

## 4. Resource and Content Validation

- Maximum encoded body: 5 MiB.
- Maximum dimensions: 10,000 by 10,000 pixels; maximum decoded pixels: 20 million.
- The decoder verifies the actual container format and rejects a declared/actual MIME mismatch.
- Animated or multi-frame images are rejected in v1.
- Decoding happens before provider execution and converts to bounded grayscale data.
- Pillow decompression-bomb warnings/errors and malformed/truncated content map to stable `OCR-V-*`
  errors without returning decoder internals.

## 5. Result Contract

[`ocr-result.v1.schema.json`](../../contracts/ocr/ocr-result.v1.schema.json) is the language-neutral
contract shared with parsing. It contains page number, text, normalized block coordinates, confidence,
duration, provider metadata, and the source SHA-256. It contains no image bytes, credentials, tenant
identity, or user identity.

Provider text is untrusted document content. Downstream prompt construction must keep it in a data
boundary and must not treat instructions found in OCR text as system or developer instructions.

## 6. Security and Observability

- Internal authentication uses a non-empty configured token and constant-time comparison.
- Tenant/user headers must be canonical UUIDs. The request ID is normalized to a bounded safe value.
- Logs and errors may include request ID, provider, dimensions, duration, and content digest prefix;
  they must not include raw bytes, recognized text, identity headers, or credentials.
- Metrics distinguish validation failures, provider failures, duration, bytes, pixels, and block count.

The static internal token is an MVP service-to-service control. Rotation and workload identity remain
deployment work before multi-host production use.

## 7. Tests and Gates

- Unit: MIME/container checks, byte/pixel/dimension/frame limits, malformed images, identity/token checks,
  deterministic recognition, low-confidence behavior, and output normalization.
- Integration: FastAPI request/response/error contract and OpenAPI exposure.
- Contract: JSON Schema validates the API result consumed by parsing.
- Benchmark: repeated recognition of the fixed OCR corpus after warmup; P99 target below 100 ms on the
  recorded development machine.
- Coverage: `engine_core` instruction coverage at least 80%.

## 8. Compatibility

This change adds one Python API and one result schema. It changes no Auth/File Upload API, database
migration, Java module, SDK, or public Plugin SPI. The existing health endpoint remains compatible.

