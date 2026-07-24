# Security Review: Issue #65 v0.3 Knowledge

> Status: Passed for draft PR | Last updated: 2026-07-24

| Threat | Control | Verification | Status |
|---|---|---|---|
| Cross-tenant or cross-base retrieval | Java checks membership; both Milvus and Elasticsearch filter tenant and base before ranking | Java authorization tests and live scoped repository test | Passed |
| Parser archive bomb or corrupt input | Raw size, archive entry, expanded-byte and output limits; encrypted/corrupt input fails closed | Real PDF/Office and hostile archive tests | Passed |
| Prompt injection and citation forgery | Retrieved text remains delimited untrusted data; citations must match retrieved chunk identity and provenance | RAG prompt and forged-citation tests | Passed |
| Provider credential disclosure | OpenAI-compatible key is deployment-only and never accepted or returned by APIs | Configuration and provider request tests plus secret scan | Passed |
| Partial dual write | Milvus write precedes Elasticsearch; index failure triggers scoped vector deletion | Compensation unit test | Passed |
| Production fallback to test adapters | Production startup rejects deterministic embedding and memory repositories | Configuration/startup test | Passed |
| Browser content injection | Excerpts and citations use React text rendering; no raw HTML path was added | Source review and frontend tests | Passed |
| Vulnerable dependency or embedded secret | pip-audit, npm high-level audit, and Trivy vulnerability/misconfiguration/secret scan | Python clean; npm no HIGH/CRITICAL; Trivy 586-file snapshot clean | Passed |

## Residual Risk

- A process crash between Milvus and Elasticsearch writes can require operator-driven re-indexing;
  durable outbox and automatic reconciliation remain required before stable release.
- Elasticsearch has security disabled in the single-node development Compose. It is container-network
  scoped and the optional host mapping is loopback-only; production must add network and engine auth.
- npm reports two MODERATE React Router advisories. No HIGH or CRITICAL issue is present under the OEP
  gate; the router upgrade will be handled with dedicated compatibility testing.
- External embedding quality, provider availability, multi-node recovery and capacity behavior are not
  established by deterministic alpha benchmarks.
