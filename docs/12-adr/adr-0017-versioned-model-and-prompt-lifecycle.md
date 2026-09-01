# ADR-0017: Versioned Model and Prompt Publication

## Status

Accepted

## Date

2026-09-01

Accepted by the Bootstrap Maintainer as part of RFC-0009.

## Context

Agent, Chat, and Workflow executions need reproducible model and Prompt choices. Mutable configuration makes
evaluation, rollback, cost accounting, and incident review unreliable.

## Decision

Represent models, provider policies, and Prompts as drafts plus immutable versions. Review and evaluation are
required before publication. Published references are pinned by execution using version IDs and content
digests. Rollback creates a new published reference to an old version and never edits history. Credentials are
secret references only; Prompt content is not copied into audit events or traces.

## Consequences

- Executions are reproducible and can be linked to evaluation and pricing evidence.
- Publication requires lifecycle state, review, and compatibility checks.
- Provider and Prompt migrations need explicit version management and storage retention policy.

## Alternatives considered

| Option | Benefit | Why not selected |
|---|---|---|
| Mutable active configuration | Simple operations | Cannot reproduce or safely roll back executions |
| Store provider credentials in registry | Convenient runtime lookup | Expands compromise and leakage impact |
| Let each runtime select a model | Local flexibility | Bypasses governance and policy authority |
