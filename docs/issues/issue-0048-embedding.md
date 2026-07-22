# Issue #48: Reproducible MVP Embedding Pipeline

## Scope

Deliver a bounded internal batch API, provider/repository ports, deterministic offline provider,
tenant-scoped memory adapter, idempotent jobs, and the canonical completion event contract.

## Acceptance

- Strict authenticated API returns normalized fixed-width vectors and model provenance.
- Exact job replay is idempotent; changed input collision fails.
- Upsert/search/delete cannot cross tenant or knowledge-base scope.
- Production cannot accidentally start with the in-memory repository.
- Tests, benchmark, security review, six gates, and documentation pass.
