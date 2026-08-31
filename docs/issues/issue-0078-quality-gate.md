# Quality Gate: Issue #78 v0.6 Agent

> Status: Validation complete; HIGH/CRITICAL release gate passed with moderate upstream residual risk
> Last updated: 2026-08-31

| Gate | Evidence | Status |
|---|---|---|
| Coverage | Python full non-benchmark suite 89.40%; frontend 34 tests with 91.77% statements, 85.64% branches, 91.81% functions and 95.07% lines; Java JaCoCo gate passed in `gradlew check` | Passed |
| Static analysis | Java Checkstyle, Spotless and SpotBugs; Python mypy, Ruff check and format; frontend lint | Passed |
| Benchmark | 500-case deterministic corpus, three repeats, 1,500 evaluated cases, zero errors; capability, bounded runtime, MCP fixture discovery and Evaluation P99 values are within thresholds | Passed |
| Security | Trivy source/runtime scans, `pip-audit`, frontend high-severity audit and website high-severity audit passed; safe image parser regression covers the former `image-size` path | Passed with 18 MODERATE upstream `uuid` findings |
| API documentation | Agent v2 OpenAPI, Java contract test, Python/TypeScript contracts, frontend API models and management surfaces updated | Passed |
| Compatibility | v1 API/SPI remains unchanged; v2 candidate/publish path validates revision and digest; migration preserves `auth_users`; MySQL contract and rollback passed | Passed |
| Integration evidence | MySQL 8.4 migration/rollback, live Milvus/Elasticsearch hybrid test, full Compose health and release smoke passed; Playwright desktop/mobile check passed | Passed |

## Decision

The v0.6 Agent implementation satisfies the implementation and validation evidence for OEP steps 7-13.
The former website `image-size` HIGH findings are removed from the installed graph by the local safe
fork, and `npm audit --audit-level=high` passes. The website development chain still has 18 MODERATE
`uuid` findings with no available fix; they are recorded as residual supply-chain risk under the current
policy, which blocks neither the HIGH/CRITICAL gate nor the completed validation evidence.

## Required follow-up before release readiness

- Revisit the safe `image-size` fork whenever Docusaurus publishes an upstream parser fix or is upgraded.
- Track the 18 MODERATE `uuid` findings and replace the Docusaurus development chain when a compatible
  upstream fix becomes available.
- Retain the local Playwright screenshot outputs in CI artifacts for future release candidates.

## Evidence commands

```text
cd java/platform
.\gradlew.bat check

cd python
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m mypy engine-core/src
.\.venv\Scripts\python.exe -m ruff check --no-cache engine-core/src tests
.\.venv\Scripts\python.exe -m ruff format --check --no-cache engine-core/src tests
.\.venv\Scripts\python.exe -m pip_audit -r requirements.lock
```

The raw benchmark result is `docs/13-testing/results/v0.6-agent-benchmark.json`; detailed threat
evidence is recorded in `docs/issues/issue-0078-security-review.md`.
