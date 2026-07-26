# v0.6 First-Party Agent Contracts

All five Agents are immutable published Agent SPI v2 versions, use governed Tools, repeat tenant/resource
authorization, and pass the common durability, security, and Evaluation gates.

| Agent    | Capabilities                                       | Safety and acceptance contract                                                                                                            |
| -------- | -------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Document | OCR, parse, summarize, extract, search             | Authorized document handles only; cite page/chunk IDs; no raw paths/URLs; bounded caller-supplied extraction schema                       |
| SQL      | schema, explain, read query, approved write        | Dialect AST validation; one statement; read-only default; row/byte/time limits; write/destructive calls need grants and per-call approval |
| BI       | governed SQL read, transform, chart, report        | Dataset/query lineage; allowlisted chart schema; no script/HTML; row-level policy survives aggregation                                    |
| Search   | lexical/vector/federated search, fetch handle      | Authorization before retrieval; stable citations; source text is untrusted and cannot grant Tools                                         |
| Workflow | list published, start, inspect, approve if granted | Pinned published version; idempotency/correlation; model cannot delegate approval identity or edit definitions                            |

Every Agent covers success, empty data, stale version, denied resource, source prompt injection, schema mismatch,
timeout/cancel, retry boundary, tenant confusion, and output limits. SQL adds mutation/exfiltration; BI adds
unsafe chart payloads; Search/Document add citation faithfulness; Workflow adds duplicate start and approval
escalation. Evidence reports task success, policy violations, quality, P50/P95/P99 latency, token/cost, flakes.
