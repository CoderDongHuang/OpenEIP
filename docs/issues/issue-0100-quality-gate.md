# Quality Gate: v0.9 Performance Baseline

| Gate | Standard | Evidence | Status |
|---|---|---|---|
| Focused tests | Boundary and contract tests pass | 25/25 passed on Python 3.12.14; module coverage 86% | Passed locally |
| Full Python tests | Existing suite remains green | Historical baseline: 190 passed, 1 skipped; 89.40% coverage | Rerun pending |
| Static analysis | Ruff and strict Mypy clean | Historical baseline only; current patch not run in local Python 3.10 environment | Rerun pending |
| Benchmark evidence | Machine-readable, reproducible, bounded | Commit `40364d0`: 200 requests, P99 28.553 ms, zero errors, threshold 100 ms | Passed locally; release-commit rerun required |
| Integration | Intended deployment boundary exercised | No Compose/multi-node run attached | Pending |
| Security | Independent review, dependency/container/secret scans | Local abuse tests only | Pending |
| Documentation | SDD, test plan, result, checklist synchronized | Present in repository | Passed |
| Release | PR review, merge, tag workflow, images, attestations | PR #122 merged without formal review; hardening PR and release tag pending | Pending |

This gate cannot be marked release-passed until all Pending rows have immutable
CI/review/release evidence.
