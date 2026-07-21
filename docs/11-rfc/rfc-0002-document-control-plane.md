# RFC-0002: Document Control Plane

## Status

Accepted (Bootstrap)

## Abstract

Introduce a Java document control-plane module and a deployable Java platform aggregator for the
v0.2 file, knowledge, and AI document pipeline. Binary content is accessed through an object storage
port; transactional metadata and access decisions remain in Java.

## Motivation

The SAD assigns transactions and authorization to Java and document processing to Python, but the
initial module list has no Java owner for uploaded files and knowledge metadata. Putting those records
in Python would invert the ownership boundary. Running every Java module as a separate service would
also contradict the modular-platform baseline and duplicate authentication configuration.

## Design

- `platform-app` is the single deployable Java composition root.
- `platform-auth`, `platform-document`, and later Java control-plane modules remain Gradle modules.
- `platform-document` owns file metadata and authorization. It never parses or embeds content.
- `ObjectStorage` is a domain port. v0.2 ships a bounded local-filesystem adapter; an S3/MinIO adapter
  may replace it without changing application or API contracts.
- Upload writes content first, then commits metadata. A metadata failure triggers best-effort object
  deletion. Delete removes metadata only after object deletion succeeds.
- Content is addressed by server-generated UUID keys. User filenames never become paths.
- The MVP runs as one logical tenant (`default`) until the governance phase adds tenant claims and
  membership. Every row already carries `tenant_id` so the schema can evolve without destructive
  migration.
- `document.file.uploaded` is defined as a language-neutral JSON Schema. Publishing to Kafka is an
  infrastructure adapter added with the asynchronous pipeline; file persistence does not depend on
  broker availability in the local MVP profile.

## Alternatives Considered

| Option | Benefit | Cost | Decision |
|---|---|---|---|
| Store files in Python | Direct parser access | Python would own transactions and authorization | Rejected |
| Separate Java service per module | Independent deployment | Duplicated auth/config and premature distributed system | Rejected |
| Store blobs in MySQL | Atomic transaction | Database growth and poor object-store portability | Rejected |
| Hard-code MinIO client | Production-like API | Makes default deployment heavier before capacity validation | Rejected |

## Impact

- API: adds `/api/v1/documents/files`; existing Auth endpoints remain unchanged.
- Database: adds `document_files` with a reversible migration.
- Deployment: Docker builds `platform-app`; the externally visible Java port remains 8080.
- Security: the existing Auth filter protects document APIs; ownership is checked in the application
  query rather than trusted from request parameters.
- Compatibility: no SDK or public Plugin SPI changes.

## Migration Plan

Existing Auth deployments can replace the Auth boot JAR with the platform aggregator against the same
database. Flyway applies only the new document table. Rollback removes `document_files` after stored
objects have been exported or deleted.

## Decision Record

The Bootstrap Maintainer accepts this v0.2 composition boundary under the same temporary governance
rule as RFC-0001. Later changes to service decomposition or public storage SPI require a new RFC.

