# Architecture Review: Issue #48 Embedding

> Decision: Approved | Date: 2026-07-22

| Check | Result |
|---|---|
| SAD runtime boundary | Embedding computation remains in Python; Java owns knowledge metadata |
| ADR-0003 storage condition | Milvus is not promoted to the default profile; repository port isolates it |
| ADR-0004 Spike scope | Reuses proven vector semantics without reusing Spike code as production code |
| Security boundary | internal token + canonical identity; tenant/base filter precedes scoring |
| Supply chain | offline provider requires no key; provider injection owns production secret handling |
| Compatibility | additive API/module; canonicalizes one unreleased stacked completion event |

The review permits coding. Real model recall, Milvus capacity, recovery, and operational promotion remain
v0.3 gates and must not be inferred from the deterministic MVP benchmark.
