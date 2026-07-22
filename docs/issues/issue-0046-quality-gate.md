# Quality Gate: Issue #46 Document Parsing

> Status: Passed
> Last updated: 2026-07-22

| Gate | Standard | Evidence | Status |
|---|---|---|---|
| Coverage | Unit + Integration >= 80% | 32 parsing-specific tests; 59 non-benchmark Python tests total; instruction coverage 97.52% (591 covered / 15 missed) | Passed |
| Static Analysis | No severe static finding | Ruff format/lint and strict Mypy pass; Java `check build` 57 tasks pass | Passed |
| Benchmark | 1 MiB parsing P99 < 250 ms | 5 warmups + 30 measurements, 1,172 chunks: P50 54.37 ms, P95 59.74 ms, P99 60.63 ms, 18.39 MiB/s at P50 | Passed |
| Security | No HIGH/CRITICAL | pip-audit 0; Trivy committed repository/secrets/misconfig 0; Debian 13.6/Python runtime 0 | Passed |
| API Docs | API/result/event contracts synchronized | Runtime OpenAPI, OCR input, parsed result and no-text event Schemas are contract-tested | Passed |
| Compatibility | API/DB/SDK/SPI compatible | Additive API/Schemas; no DB/SPI change; 25 OCR regressions, Java build, Compose container, and upstream six-item CI pass | Passed |

## Integration Evidence

- Isolated Compose builds and starts the aggregate Python engine healthy, then serves an authenticated
  `text/plain` parse request with a standard result, parser metadata, hashes, and chunks.
- OCR `ocr-result.v1` is consumed without an adapter-specific dependency; mismatched top-level/block text
  and duplicate/extra JSON fields are rejected.
- OCR stacked PR #53 has all six remote CI checks passing. Parsing introduces no frontend or Java source
  change; the local Java aggregate build still passes.
- The result event is intentionally not published. The report only validates its versioned Schema.

Benchmark evidence: [`document-parsing-benchmark.json`](../13-testing/results/document-parsing-benchmark.json).

## Reproduction

```powershell
cd python
.venv/Scripts/ruff check .
.venv/Scripts/ruff format --check .
.venv/Scripts/mypy engine-core/src
.venv/Scripts/pytest --cov-report=xml
$env:OPENEIP_PARSING_BENCHMARK_OUTPUT='../docs/13-testing/results/document-parsing-benchmark.json'
.venv/Scripts/pytest -m benchmark --no-cov
.venv/Scripts/pip-audit -r requirements.lock

cd ..
docker compose -p openeip-parsing-test up -d --build python
docker compose config --quiet
```
