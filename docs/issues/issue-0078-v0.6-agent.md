# Issue #78: v0.6 Production Agent Platform

GitHub: https://github.com/CoderDongHuang/OpenEIP/issues/78  
Milestone: `v0.6.0 Agent` (#3)

## Scope

Deliver Tool, Memory, Planner, Multi-Agent, MCP, and Evaluation as one tenant-safe Agent platform.
The first release Agents are Document, SQL, BI, Search, and Workflow. Meeting, Coding, Finance,
Legal, and HR remain candidates and are not v0.6 acceptance evidence.

The management experience includes Agent definitions and versions, execution timelines, Tool grants,
Memory administration, MCP Server administration, and an Evaluation Dashboard.

## OEP Evidence

| Step                  | Evidence                                        | State                                 |
| --------------------- | ----------------------------------------------- | ------------------------------------- |
| 1 Issue               | GitHub #78 and milestone #3                     | Complete                              |
| 2 RFC                 | RFC-0008 and Discussion #80                     | Accepted 2026-08-31                    |
| 3 ADR                 | ADR-0012, ADR-0013, ADR-0014                    | Accepted 2026-08-31                    |
| 4 Module Design       | Agent v0.6 SDD, SPI v2, first-party contracts   | Review packet complete                |
| 5 API/DB/UI Design    | Agent v2 OpenAPI, schema, events, UI            | Review packet complete                |
| 6 Architecture Review | Architecture and security design review records | Approved 2026-08-30                   |
| 7 Implementation | Java Agent v2 control plane, Python Agent v2 runtime, frontend management surfaces and Compose configuration | Complete 2026-08-31 |
| 8 Unit Test | Java, Python and frontend unit/contract suites; coverage and static checks recorded in the Quality Gate | Complete 2026-08-31 |
| 9 Integration Test | MySQL 8.4 migration/rollback, live Milvus/Elasticsearch hybrid repository, complete Compose health and release smoke | Complete 2026-08-31 |
| 10 Benchmark | Capability verification, bounded runtime, MCP fixture discovery and deterministic 500-case Evaluation benchmark | Complete 2026-08-31 |
| 11 Security Review | Executable Agent abuse controls, dependency/container scans and website safe image parser regression | Complete with moderate upstream residual 2026-08-31 |
| 12 Quality Gate | `issue-0078-quality-gate.md`; HIGH/CRITICAL gate passed, with moderate upstream `uuid` residual documented | Passed with residual risk 2026-08-31 |
| 13 Docs Update | RFC/ADR decisions, SDD/SPI/OpenAPI/schema, test plan, security review, benchmark and gate evidence | Complete 2026-08-31 |
| 14 Pull Request | Implementation PR #97 merged; release preparation PR | In progress |
| 15 Code Review | Formal Review was absent on implementation PR #97; release preparation requires independent review | Required before release |
| 16 Merge | Implementation PR #97 merged as `4f7a3fb`; release preparation merge pending | Partial |
| 17 Release | `v0.6.0-alpha` Tag, Release workflow and GitHub Pre-release | Not started |

No implementation may begin until RFC discussion and voting complete, the ADRs are accepted, and
the architecture and security design reviews are independently approved.

## Process correction

PR #97 was merged after all six CI checks passed, but GitHub records no formal review on that PR. CI is
not a substitute for Code Review. The v0.6 release is therefore blocked until an independent maintainer
reviews the release preparation PR and confirms the implementation, compatibility, security, and evidence
records. The missing pre-merge review is recorded rather than retroactively represented as complete.

## Implementation and evidence notes

- The first publish path freezes an Agent Draft into an immutable `CANDIDATE`. Evaluation pins that
  candidate and the published baseline; publish promotes the same candidate row after revision and
  digest validation. Candidate creation does not consume an integer release version.
- Java verification completed with `java/platform/gradlew.bat check`, including the Agent v2 API
  contract tests and MySQL 8.4 Testcontainers migration/rollback contract.
- Python verification completed with 174 tests passed and one environment-gated test skipped; the
  full non-benchmark suite reports 89.40% coverage, mypy and Ruff checks pass, and `pip-audit`
  reports no known vulnerabilities.
- Frontend verification completed with 34 tests, 91.77% statements, 85.64% branches, 91.81%
  functions, 95.07% lines, lint passing and a production build passing.
- The benchmark artifact is `docs/13-testing/results/v0.6-agent-benchmark.json`. It evaluates 500
  cases over three repeats (1,500 cases) with zero errors and passing P99 thresholds.
- The Docusaurus `image-size` HIGH findings were removed from the installed dependency graph by a
  reviewed local safe fork. The fork bounds input to 512 KiB, supports only the documentation formats
  required by this site, and disables the affected ICNS/JXL/HEIF parsers. `npm audit --audit-level=high`
  now exits successfully; the upstream Docusaurus development chain still reports 18 MODERATE `uuid`
  findings with no available fix.
- Playwright desktop/mobile checks passed without horizontal overflow or broken images, and the docs
  navigation reached `/OpenEIP/docs/intro`. The full Compose smoke and live Milvus/Elasticsearch test
  also passed on the local Docker Desktop environment.
