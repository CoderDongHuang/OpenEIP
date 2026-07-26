# ADR-0013: Govern Agent Memory with MySQL Metadata and Tenant-Partitioned Stores

## Status

Proposed

## Date

2026-07-26

## Context

Memory may retain sensitive, stale, or poisoned content. Vector similarity cannot enforce tenant, purpose,
principal, retention, provenance, and deletion policy.

## Decision

Working Memory stays ephemeral. Session Memory uses Redis tenant-prefixed keys, absolute TTL, and byte/item
limits. Long-term metadata is authoritative in MySQL; embeddings use tenant-partitioned Milvus keyed by opaque
IDs. Entries record source, purpose, sensitivity, consent/legal basis where applicable, owner scope, lineage,
embedding version, deadline, and deletion state. Model assertions remain unverified until accepted by policy
or user action. Reads prefilter authorized metadata and recheck vector results. Memory excludes chain-of-thought,
provider prompts, credentials, tokens, and unrestricted Tool output. Tombstone denies reads immediately; an
idempotent job purges Redis/Milvus/derived copies and retains only non-content deletion proof.

## Consequences

### Positive

- Authorization, retention, provenance, and deletion remain enforceable outside vector search.
- Existing MySQL, Redis, and Milvus responsibilities are reused.

### Negative

- Cross-store deletion and reconciliation add operational complexity.
- Metadata filtering can reduce recall and add latency.

### Risks

- Tenant leakage is catastrophic; partitions, rechecks, adversarial tests, and tenant metrics are mandatory.
- Derived summaries carry lineage and must be purged transitively.

## Alternatives Considered

| Option                              | Benefit          | Why not selected                          |
| ----------------------------------- | ---------------- | ----------------------------------------- |
| All Memory in Milvus                | Simple retrieval | No authoritative lifecycle/deletion state |
| Persist all conversations           | Maximum recall   | Violates minimization and purpose limits  |
| Shared collection with tenant field | Efficient        | Filter defects have unacceptable impact   |
