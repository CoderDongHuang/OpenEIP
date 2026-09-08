# Marketplace Module SDD

## Scope

v0.8.0-alpha introduces the first Marketplace slice: a tenant-scoped catalog for Plugin, Connector,
and Agent packages. A package has immutable version artifacts with a URI, SHA-256 digest, runtime,
and manifest. Artifact bytes are stored by an external registry; this module stores only the signed
catalog reference and lifecycle evidence.

## Lifecycle

`DRAFT -> REVIEWED -> PUBLISHED -> SUSPENDED`

Only a reviewed version can be published. Every lifecycle command requires `Idempotency-Key` and
`If-Match` for optimistic concurrency. The database uniqueness key `(tenant_id, package_id, version)`
prevents duplicate releases. Public discovery returns only packages whose catalog state is `PUBLISHED`.

## Trust boundary

All management operations use the server-derived Governance tenant context. Package and version reads
are tenant-filtered in SQL; a caller cannot address another tenant's identifier. SHA-256 is required as
lowercase hexadecimal and SemVer is required for version labels. Artifact download and signature
verification remain responsibilities of the external registry integration and are not implied by this API.

## Compatibility

The module is additive: it adds `platform-marketplace`, migration `V2.8.0`, and `/api/v1/marketplace`
endpoints. Existing Governance, Agent, Connector, event, and SDK contracts are unchanged. Future
manifest fields must be additive; changing lifecycle semantics requires a new API version.
