# Architecture Review: Issue #65 v0.3 Knowledge

## Result

Approved for implementation against RFC-0005 and ADR-0009.

- Java remains the only public authorization decision point.
- Python APIs remain internal-token authenticated and identities are canonical UUIDs.
- Tenant/base filters execute inside Milvus and Elasticsearch before ranking.
- Parser adapters enforce raw, archive-entry, expanded-byte, and chunk limits.
- Source content is untrusted data and cannot alter prompts or citation allowlists.
- Production startup rejects deterministic/in-memory persistence configurations.

Quality-gate evidence includes a live Milvus 2.6 and Elasticsearch 8.19 integration test for scoped
upsert/search/delete. Unit tests verify that an Elasticsearch write failure triggers scoped Milvus
compensation. Automatic reconciliation after a process crash remains explicit stable-release hardening.
