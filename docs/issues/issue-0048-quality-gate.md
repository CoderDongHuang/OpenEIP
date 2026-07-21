# Quality Gate: Issue #48 Embedding

> Status: Passed | Last updated: 2026-07-22

| Gate | Standard | Evidence | Status |
|---|---|---|---|
| Coverage | Unit + Integration >= 80% | 27 Embedding tests; 86 full non-benchmark tests; total coverage 97.91% (845 covered / 18 missed) | Passed |
| Static Analysis | No severe static finding | Ruff format/lint and strict Mypy pass; aggregate Java `clean check build` passes 75 tasks | Passed |
| Benchmark | 32-text batch P99 < 50 ms | 5 warmups + 100 samples at 64 dimensions: P50 0.658 ms, P95 0.992 ms, P99 1.359 ms | Passed |
| Security | No HIGH/CRITICAL | pip-audit 0; Trivy repository/secret/misconfig, Java runtime, Python image 0 | Passed |
| API Docs | API/event docs synchronized | runtime OpenAPI path and canonical `embedding.job.completed.v1` Schema are contract-tested | Passed |
| Compatibility | API/DB/SDK/SPI compatible | additive Python API/ports; canonical event rename is contained in the unreleased stacked branch | Passed |

## Integration Evidence

- Isolated Compose builds the fixed Debian 13.6/Python 3.12.13 image, reaches healthy state, and serves
  an authenticated batch request with HTTP 200, deterministic model provenance, dimension 64, and one
  persisted vector.
- `InMemoryVectorRepository` proves exact upsert, scoped search and delete behavior. It is not enabled
  as a production repository and no Milvus production claim is made.
- The Knowledge Kafka listener and Java event contract tests consume the canonical
  `embedding.job.completed` shape after the stacked contract rename.
- Full Python suite: 86 passed, 3 benchmarks deselected, total coverage 97.91%. Independent benchmark
  run executes Embedding, OCR and Parsing benchmarks with coverage disabled; all pass.

Benchmark evidence: [`embedding-benchmark.json`](../13-testing/results/embedding-benchmark.json).

## Compatibility Decision

The API, Python ports, configuration fields, and event Schema are additive. The previous temporary
`knowledge.embedding.completed` contract existed only in unmerged PR #55; this stacked PR replaces it
with Issue #48's canonical event before any release. Existing Auth/File/OCR/Parsing APIs and public
Plugin SPI are unchanged.
