# Quality Gate: v0.9 Performance Baseline

| Gate | Standard | Evidence | Status |
|---|---|---|---|
| Focused tests | Boundary and contract tests pass | 16 tests passed locally | Passed locally |
| Full Python tests | Existing suite remains green | 190 passed, 1 skipped; 89.40% coverage | Passed locally |
| Static analysis | Ruff and strict Mypy clean | Ruff and Mypy passed locally | Passed locally |
| Benchmark evidence | Machine-readable, reproducible, bounded | 200 requests, 4 concurrency, P99 19.203 ms, zero errors | Passed for local fixture |
| Integration | Intended deployment boundary exercised | No Compose/multi-node run attached | Pending |
| Security | Independent review, dependency/container/secret scans | Local abuse tests only | Pending |
| Documentation | SDD, test plan, result, checklist synchronized | Present in repository | Passed |
| Release | PR review, merge, tag workflow, images, attestations | PR #122 open; no release tag | Pending |

This gate cannot be marked release-passed until all Pending rows have immutable
CI/review/release evidence.
