# Agent v0.6 Database Design

> Migration target: `V2.6.0` (additive, forward-only) | Status: Proposed

All tables use binary UUID primary keys, `tenant_id`, UTC timestamps, optimistic `revision`, and indexes that
start with `tenant_id`. Foreign keys include or are application-validated against the same tenant. JSON columns
are schema-validated before persistence and never contain credentials, prompts, thoughts, or raw Tool results.

| Table group | Tables                                                                                                                                                     | Key invariants                                                                                  |
| ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Agent       | `agent_definition`, `agent_version`                                                                                                                        | one draft; published rows immutable; unique tenant/name and definition/version; SHA-256 digest  |
| Tool        | `tool_definition`, `tool_version`, `agent_tool_grant`                                                                                                      | immutable exact version; grant pins Agent/Tool version, selector, constraints, approval, expiry |
| Run         | `agent_run`, `agent_run_dependency`, `agent_step`, `agent_attempt`, `agent_handoff`, `agent_command`, `agent_checkpoint`                                   | revision state machine; lease/fencing; idempotency; sanitized references only                   |
| Timeline    | `agent_run_event`, `agent_outbox`                                                                                                                          | unique run/sequence and tenant/idempotency; append-only; safe payload schema                    |
| Memory      | `agent_memory_policy`, `agent_memory_entry`, `agent_memory_lineage`, `agent_memory_purge_job`                                                              | purpose/provenance/sensitivity/deadline; tombstone before purge; opaque vector ID               |
| MCP         | `mcp_server`, `mcp_server_version`, `mcp_capability`, `mcp_tool_mapping`                                                                                   | credential reference only; discovery digest; mapping suspended on drift                         |
| Evaluation  | `eval_dataset`, `eval_dataset_version`, `eval_case`, `eval_suite`, `eval_suite_version`, `eval_run`, `eval_case_result`, `eval_metric`, `eval_gate_result` | immutable versions; pinned candidate/baseline/scorer/environment                                |

## State constraints

`agent_run.status` uses the eight SDD states. `agent_attempt` is unique by `(tenant_id, step_id,
attempt_number)`; commits require the current lease owner, unexpired lease, and matching fencing token. Command
deduplication is unique by `(tenant_id, run_id, idempotency_key)`. Events are unique by `(tenant_id, run_id,
sequence)` and sequence is allocated transactionally.

Published Agent/Tool/dataset/suite versions reject update/delete. Archive is metadata on the owning definition.
Run dependency rows store exact IDs, versions, digests, and safe configuration hashes for replay.

## Memory and deletion

Entry content is encrypted using the platform envelope-encryption provider; keys are tenant scoped. Searchable
metadata and encrypted content are separated. `vector_entry_id` is opaque and its Milvus partition is derived
only from authenticated tenant context. `deleted_at` immediately excludes reads. Purge jobs are idempotent and
track Redis, vector, derived-lineage, and backup-expiry stages without retaining deleted content.

## Retention and partitioning

Run events/checkpoints, session Memory, long-term Memory, MCP discovery snapshots, and Evaluation fixtures have
separate tenant policy references and absolute deadlines. Cleanup queries use `(tenant_id, retention_deadline,
id)` indexes. High-volume `agent_run_event` and `eval_case_result` support time partitioning without changing
logical keys. Outbox cleanup occurs only after acknowledged publication and the audit retention window.

## Migration and rollback

The migration creates new tables/indexes only and does not alter v1 Agent tables. Application rollback leaves
the new tables unused. Data rollback is an explicit later migration; released migration identifiers are never
reused. A compatibility registration for the v1 Agent is created by an idempotent application command, not by
DDL, and creates no persistent Memory or Tool grant.
