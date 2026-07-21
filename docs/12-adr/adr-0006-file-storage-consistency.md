# ADR-0006: File Storage Consistency and Security Boundary

## Status

Accepted

## Date

2026-07-22

## Context

File upload spans a transactional metadata store and a non-transactional object store. User-supplied
names and media types are untrusted, authorization must not be delegated to object keys, and a partial
failure must not leave a downloadable record pointing at missing content.

## Decision

- MySQL owns file identity, owner, tenant, digest, media type, size, object key, and lifecycle state.
- Object content is written through `ObjectStorage`; the generated object key is unrelated to the
  original filename and is never accepted from the API.
- Upload streams into the store with a hard byte limit while computing SHA-256. Metadata is committed
  only after the object write succeeds. A database failure triggers best-effort object cleanup.
- Download first resolves an owner-scoped metadata record and then opens the object. Object paths are
  never derived from request parameters.
- Delete first deletes the object and then the metadata in one application transaction. Missing object
  deletion is idempotent; other storage errors preserve metadata for retry.
- Original filenames are normalized to a basename, reject control characters, and are encoded only in
  RFC 5987 `Content-Disposition` parameters.
- Client media type and extension must be in the MVP allowlist. Later parser stages still inspect
  content and must not treat this metadata as proof of format.

## Consequences

The design prevents path traversal and most orphan metadata states without distributed transactions.
A process crash between object write and metadata commit can still leave an unreferenced object; a
future reconciliation job may delete objects not referenced after a grace period. The local adapter is
single-node only and is not a production HA object store.

## Alternatives Considered

| Option | Benefit | Cost | Decision |
|---|---|---|---|
| XA transaction | Strong atomicity | Object stores do not generally participate; high complexity | Rejected |
| Database blob | Single transaction | Poor scaling and portability | Rejected |
| Presigned direct upload | Lower server bandwidth | Requires S3 baseline and multipart authorization design | Later |

