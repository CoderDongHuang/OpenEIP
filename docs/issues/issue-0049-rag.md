# Issue #49: Permission-Aware RAG Pipeline

## Scope

Deliver a bounded internal query API, tenant/base pre-filtered retrieval, canonical untrusted-context
prompt, replaceable answer port, deterministic offline answer fixture, and verified citations.

## Acceptance

- Search filters before scoring and cannot reveal another tenant/base.
- Context text cannot alter system policy or citation allowlist.
- Every citation maps to an actual retrieved chunk; forged citations fail closed.
- Tests, benchmark, security review, six gates, and docs pass.
