# v0.9 范围与流程纠偏记录

> 核查日期：2026-10-26。结论：第九模块未完整交付，尚不满足发布条件。
> 本记录纠正此前将 benchmark 切片等同于 Performance 模块完成的判断。

## 依据与核查边界

OEP 的版本列车、PRD 第 12 章、发布策略及 `docs/15-roadmap/README.md`
均列出“压测 / 调优 / 高可用 / 扩容 / 稳定性”。PRD 0.4 同时要求每个后续模块先补齐
用户故事、验收标准、子 SDD、威胁模型和基准指标；alpha 允许不完整，不等于完成整个模块。
SAD 1.3 要求无状态服务，9.2/9.3 给出多副本、HPA 与数据层 HA，11 章规定可观测性。

本次核对上述基线、OEP 17 步与六项门禁、治理文档、性能 RFC/ADR/SDD/契约、部署、
测试、发布记录，并检查其对应代码和工作流。没有将仓库中所有历史文档、所有业务模块重新验收；
原有模块仅在与本次性能、多副本和恢复路径有关的边界内检查。

## 要求 → 实现 → 验证 → 缺口

| ID | 上位要求 | 当前证据 | 缺口 / 后续交付 |
|---|---|---|---|
| PERF-001 | 压测 | GET 工具、25 tests、fixture P99 34.292 ms | 缺少已认证业务写入、RAG、OCR、Embedding、Agent、SSE 的统一负载与回归比较 |
| PERF-002 | 调优 | 各模块单独 microbenchmark | 缺少瓶颈定位、同机同负载前后比较、连接池/并发/队列边界及回滚 |
| PERF-003 | 应用 HA | Compose 每类服务一个实例 | 缺多副本部署、稳定密钥、共享文件、依赖就绪、摘流和滚动故障验证 |
| PERF-004 | 扩容 | SAD 规划 HPA；无可部署产物 | 缺 requests/limits、HPA、PDB、拓扑分散、扩缩容与数据库连接预算测试 |
| PERF-005 | 稳定性 | fixture 短时 HTTP 测量 | 缺长稳、故障注入、恢复、备份恢复与跨存储对账证据 |
| PERF-006 | 可观测性 | 既有 Governance trace；Java health/info、Python `/health` | 缺性能指标、Prometheus/Grafana、跨运行时 OpenTelemetry 验证和告警演练 |
| PERF-007 | 共享状态安全 | Auth 进程限流、Embedding 进程去重 | 副本间限流与去重不一致；不能直接打开 HPA |
| PERF-008 | 数据层 HA | MySQL 单实例、Redis 不持久、Milvus standalone、ES single-node | 缺 SAD 9.3 各组件拓扑、实际切换/恢复和数据一致性报告 |
| PERF-009 | 兼容与发布 | PR #127 合并；六项 CI jobs 成功 | CI 的整栈步骤被分支条件跳过；缺候选整栈、迁移回滚、镜像和发布证据 |

代码定位：

- `deploy/docker-compose/docker-compose.yml`：临时 JWT、本地文件卷、单节点数据层配置。
- `java/platform/platform-auth/src/main/java/com/openeip/auth/infrastructure/security/FixedWindowRateLimiter.java`：进程内计数。
- `python/engine-core/src/engine_core/embedding/application/service.py`：`OrderedDict` 去重且全局锁覆盖 Provider I/O。
- `python/engine-core/src/engine_core/main.py`：`/health` 只返回静态状态。
- `java/platform/platform-workflow/src/main/java/com/openeip/workflow/application/WorkflowTriggerService.java`：`fireDue` 查询需补双实例竞争验证；不能仅凭本地定时器认定分布式安全。
- `.github/workflows/ci.yml`：整栈仅在 `release/` PR 或 `v*` tag 执行，`codex/v0.9-*` 的成功结果不能证明整栈通过。
- `.github/workflows/release.yml`：手动输入 ref 未贯穿 checkout/verify/镜像标签，存在产物与输入引用不一致风险，修复前使用真实 tag 路径。

## 不能补写为已完成的流程事实

PR #127 于 2026-09-12 获 `WriteBigBug` 正式 Approval 并合并为
`993569acbe400384fc48d884f9d3ef4bc08fe4e1`。这证明 benchmark 代码接受过审查，
不能证明完整 Performance 设计通过了编码前架构审查。
Discussion #128 在实现合并后才创建；截至本次核查没有评论或维护者投票。
旧讨论的一周时间已过，但“经过一周”不等于“RFC 已接受”，也不覆盖新增的完整设计。
未发现独立架构/安全接受记录。历史顺序问题保留，不倒签、不补造审批、不重写提交历史。

## 纠偏交付及执行顺序

1. 恢复全部五项范围，补充 PRD PERF-001～009、完整 SDD、运维接口/状态设计、部署与验收方案。
2. 新增 RFC-0012、ADR-0021，保留 RFC-0011/ADR-0020 为 GET 工具的窄范围历史记录。
3. 将完整设计公开讨论并取得维护者决定、ADR 接受和 OEP Step 6 架构审批；本轮设计包仍为 Proposed。
4. 审批后按 SDD 工作包实现；逐包提供测试、真实集成、基准、安全与兼容证据。
5. 六项质量门禁全部通过后提交实现 PR，至少一位正式 Approve，再合并。
6. 在最终候选提交上运行整栈/多节点验证，再创建新标签，由 Release CI 发布镜像和 Release。

当前提交是可审查的纠偏设计包，不是完整实现，也不是发布批准。完整设计尚未获批，
不得以本轮文档代替实际功能、运行结果或外部审查。

## 本轮设计验证（2026-10-26）

- 已补充[运维 OpenAPI](../06-api/performance-management.openapi.json)和
  [三类证据 Schema](../../contracts/performance/performance-evidence.v1.schema.json)，
  分别通过 OpenAPI 3.0.3 与 JSON Schema Draft 2020-12 规范校验。
  使用 jsonschema 4.25.1 / openapi-spec-validator 0.7.2；三类合成 INCOMPLETE 对象的
  正向及未知字段/错误 SHA/时间/类型/负数等反向结构检查共21项通过。
  合成数据只用于结构校验，没有写为性能/HA 实测产物。
- 最终复核本轮文档的206个本地链接通过。
- website 的 npm run build 成功；git diff --check 通过。
- 既有 GET 套件本轮 Python 3.12.14 重跑25/25通过；因本地环境缺 pytest-asyncio
  与旧 pytest cache 目录写权限产生警告，不影响这25项结果，不据此声称全 Python 套件通过。
  86%覆盖率仍引用历史 CI，本轮未重测覆盖率。
- 本轮未修改运行代码/CI/部署配置，未执行完整业务负载、多节点、数据切换、24h长稳或发布。
  新增功能仍待编码前决定；没有将既有绿色 CI 复用为本轮提交的验证结果。

上述命令、状态对应包含本记录的纠偏提交；GitHub CI 另以该 PR 的实际 head SHA 为准。

## 可用于项目记录的文字

2026-10-26 对第九模块 Performance 重新核查，确认此前仅完成 bounded HTTP benchmark，
遗漏上位文档列明的调优、高可用、扩容和稳定性交付，并存在 RFC/架构审查晚于编码的流程缺口。
已保留 PR #127 的合并与测试事实，撤回“模块技术上已可发布”的判断，恢复完整需求范围，
形成需求追踪、完整设计、状态/接口、部署、测试及发布门禁方案。新增功能实施须先取得完整设计
的 RFC/ADR 与架构审批；真实实现、测试、安全审查、质量门禁、PR Review、合并和发布分别记录，
不以旧 fixture 数据或单次 CI 成功替代完整验收。
