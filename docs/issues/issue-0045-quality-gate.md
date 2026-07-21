# Quality Gate: Issue #45 OCR

> Status: Passed
> Last updated: 2026-07-22

| Gate | Standard | Evidence | Status |
|---|---|---|---|
| Coverage | Unit + Integration >= 80% | 27 non-benchmark tests; total Python instruction coverage 96.21% (330 covered / 13 missed) | Passed |
| Static Analysis | No severe static finding | Ruff format/lint and strict Mypy pass; full Java Checkstyle/Spotless/SpotBugs build and Frontend lint/format pass | Passed |
| Benchmark | No regression; OCR P99 < 100 ms | 10 warmups + 100 full decode/recognize measurements: P50 19.40 ms, P95 22.96 ms, P99 24.07 ms, 51.55 images/s at P50 | Passed |
| Security | No HIGH/CRITICAL | pip-audit 0; Trivy committed repository 0; Python Debian 13.6 runtime and all Python packages 0 | Passed |
| API Docs | OpenAPI/result contract synchronized | Runtime OpenAPI operation and `ocr-result.v1` required fields are contract-tested | Passed |
| Compatibility | API/DB/SDK/SPI compatible | Additive Python API/schema; no DB migration; Java build, Auth/File Upload, Frontend, docs, Compose and Spike checks pass | Passed |

## Integration Evidence

- Isolated Compose builds the Python 3.12.13 image, reaches healthy state, and executes a real
  authenticated `/api/v1/ocr/recognitions` request inside the container.
- 28 OCR-related tests are split into 27 coverage-instrumented tests plus one isolated Benchmark test;
  Benchmark runs without coverage probes both locally and in CI.
- Java `clean check build` completes 61 tasks; Frontend production build and Docusaurus production build
  pass; Compose and all five Spike evidence manifests remain valid.
- The first container scan exposed 41 old-base-image findings. After the pinned image upgrade, the same
  scan reports zero; CI now scans both Java and Python runtimes.

Benchmark evidence: [`ocr-benchmark.json`](../13-testing/results/ocr-benchmark.json).

## Reproduction

```powershell
cd python
.venv/Scripts/ruff check .
.venv/Scripts/ruff format --check .
.venv/Scripts/mypy engine-core/src
.venv/Scripts/pytest --cov-report=xml
$env:OPENEIP_OCR_BENCHMARK_OUTPUT='../docs/13-testing/results/ocr-benchmark.json'
.venv/Scripts/pytest -m benchmark --no-cov
.venv/Scripts/pip-audit -r requirements.lock

cd ..
docker compose -p openeip-ocr-test up -d --build python
docker compose config --quiet
```
