# File Upload Module Design (Sub-SDD)

> Version: 1.0 | Date: 2026-07-22 | Status: Approved for Implementation
> Issue: [#44](https://github.com/CoderDongHuang/OpenEIP/issues/44) | RFC: [RFC-0002](../11-rfc/rfc-0002-document-control-plane.md) | ADR: [ADR-0006](../12-adr/adr-0006-file-storage-consistency.md)

## 1. Responsibilities and Boundaries

`platform-document` owns upload metadata, owner authorization, lifecycle, download, and deletion. It
does not perform OCR, parsing, chunking, embedding, or model calls. `platform-app` composes Auth and
Document modules into one Java runtime.

## 2. API First

The source contract is [file-upload-v1.openapi.yaml](../06-api/file-upload-v1.openapi.yaml).

| Method | Path | Result |
|---|---|---|
| POST | `/api/v1/documents/files` | Stream one multipart file and create metadata |
| GET | `/api/v1/documents/files` | List the caller's files; admins list all |
| GET | `/api/v1/documents/files/{id}` | Read owner-scoped metadata |
| GET | `/api/v1/documents/files/{id}/content` | Stream content with safe download headers |
| DELETE | `/api/v1/documents/files/{id}` | Idempotently delete content and metadata |

JSON uses the standard envelope. Content streaming uses its own media type and does not use the JSON
envelope. Page numbering starts at 1. A non-admin cannot select another owner or infer whether another
user's UUID exists; both cases return `DOC-E-001`.

## 3. Validation

- Maximum size defaults to 10 MiB and is enforced both by Spring multipart limits and the storage
  stream wrapper. Empty files are rejected.
- Allowed media types: `text/plain`, `application/pdf`, `image/png`, `image/jpeg`.
- Allowed suffixes: `.txt`, `.pdf`, `.png`, `.jpg`, `.jpeg`; suffix and media type must agree.
- Filename is reduced to a basename and rejects NUL/control characters, blank names, hidden traversal
  segments, and names longer than 255 characters.
- Server-generated UUID object keys use two-level prefixes and cannot escape the configured root.

## 4. Data and Lifecycle

See [file-upload-schema.md](../05-database/file-upload-schema.md).

```text
multipart stream -> ObjectStorage.put -> document_files INSERT -> response
                         |                      |
                         |                      + failure -> ObjectStorage.delete compensation
                         + failure -> no metadata
```

`READY` means the object and metadata are available. OCR/parse stages own later processing state in the
knowledge pipeline and do not mutate raw file identity. SHA-256 is computed during the single write.

## 5. Storage Port

`ObjectStorage.put`, `open`, and `delete` are internal Java interfaces, not a public Plugin SPI. The
local adapter opens files with create-new semantics, uses a configured root resolved at startup, and
verifies every resolved path stays below that root. Symlinks are not followed.

## 6. Event Contract

[`document.file.uploaded.v1.schema.json`](../../contracts/events/document.file.uploaded.v1.schema.json)
is the source of truth. It contains identifiers and metadata only, never raw content or credentials.
The later event adapter must use at-least-once delivery and a transactional outbox before Kafka is made
a required dependency. This module does not claim event publication before that adapter exists.

## 7. Security and Observability

The Auth module supplies a user UUID principal and current database authorities. All repository queries
include tenant plus owner unless `ROLE_ADMIN`. Logs include request ID and file UUID but never content,
authorization headers, or storage root. Metrics include accepted/rejected upload counts, bytes, storage
latency, and compensation failures.

## 8. Tests and Gates

- Unit: filename/media validation, byte limit, digest, safe path, compensation, ownership.
- Integration: authenticated multipart API, list/details/download/delete, error envelope.
- Contract: OpenAPI operations, JSON event schema, MySQL migration/rollback.
- Benchmark: upload 1 MiB payload after warmup; P99 target below 250 ms on local storage.
- Coverage: module instruction coverage at least 80%, excluding application entry points.

## 9. Compatibility

This module only adds APIs, a table, event schema, and internal ports. It changes no Auth API, SDK, or
public Plugin SPI. Database rollback and the previous `platform-auth` boot task remain supported.

