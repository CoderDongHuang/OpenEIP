# Quality Gate: Issue #65 v0.3 Knowledge

> Status: Passed for draft PR | Last updated: 2026-07-24

| Gate | Standard | Evidence | Status |
|---|---|---|---|
| Coverage | Unit + Integration >= 80% | Python 170 passed at 92.54%; frontend 27 passed at 90.64% statements; Java all-module JaCoCo verification passed | Passed |
| Static Analysis | No severe static finding | Python Ruff format/lint and strict Mypy; Java Checkstyle, SpotBugs and Spotless; frontend ESLint, Prettier and TypeScript build | Passed |
| Benchmark | Recorded baseline without regression | 1,000-record hybrid RAG: P50 5.060 ms, P95 7.181 ms, P99 8.662 ms, exact Top-1; live Milvus/Elasticsearch integration passed | Passed |
| Security | No HIGH/CRITICAL | pip-audit clean; npm high gate clean; Trivy 586-file vulnerability, misconfiguration and secret scan clean | Passed |
| API Documentation | Contracts synchronized | Parsing and retrieval OpenAPI, storage schema, UI design, SDDs, RFC-0005 and ADR-0009 | Passed |
| Compatibility | API/DB/SDK/SPI compatible | Additive media types, endpoint and citation fields; no relational migration, SDK removal or SPI break | Passed |

The local Java run used one Gradle worker and disabled Testcontainers Ryuk because Docker Desktop was
limited to 4 GiB; application-owned Testcontainers still stopped their MySQL containers. The complete
test and JaCoCo run passed. A later repeated aggregate `check` encountered a transient Docker named-pipe
probe timeout after all non-Knowledge static checks; the only source finding was fixed and the affected
Knowledge SpotBugs, Checkstyle and Spotless tasks passed on rerun.
