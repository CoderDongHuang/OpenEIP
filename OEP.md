# OpenEIP Engineering Process (OEP)

> OpenEIP 唯一研发流程规范。所有开发、设计、Review 必须遵守。
> 本文档是项目工程流程的权威定义，永久有效。

---

## OpenEIP Engineering Principles（十条工程原则）

> 位于 `docs/00-governance/engineering-principles.md`。所有开发、设计、Review 必须遵守。

| # | 原则 | 含义 |
|---|---|---|
| 1 | **Documentation First** | 文档先行，代码依据文档实现 |
| 2 | **Design Before Implementation** | 先设计再编码，不边写边改 |
| 3 | **API First** | 先定义 API 契约，后实现 |
| 4 | **Plugin First** | 所有扩展点通过 SPI/SDK，不做硬编码 |
| 5 | **Event Driven** | 模块间通过事件通信，松耦合 |
| 6 | **AI Native** | AI 不是外挂功能，是平台原生能力 |
| 7 | **Everything Observable** | 所有组件可监控、可追踪、可度量 |
| 8 | **Security by Default** | 安全不是事后补丁，是默认配置 |
| 9 | **Community Driven** | RFC 公开讨论，决策透明 |
| 10 | **Backward Compatibility** | API / SDK / SPI 向后兼容 |

---

## 文档目录体系（永久固定，不再修改）

```
docs/
├── 00-governance/         工程原则 / Maintainers / Committers / 治理规则
├── 01-vision/             项目愿景 / Branding / Mission
├── 02-prd/                产品需求文档（zh-CN / en-US）
├── 03-sad/                软件架构设计（zh-CN / en-US）
├── 04-sdd/                设计基线 + 各模块子 SDD
├── 05-database/           ER 图 / DDL / Migration / 索引规范
├── 06-api/                OpenAPI 规范 / 错误码 / 分页 / 认证
├── 07-agent/              Document / SQL / BI / Workflow / Meeting / Coding / ...
├── 08-connector/          MySQL / Kafka / 飞书 / 企业微信 / Git / ...
├── 09-workflow/           Node / Canvas / Execution / Trigger / Approval
├── 10-deployment/         Docker / Compose / K8S / Helm / Nginx
├── 11-rfc/                RFC-0001 ~ RFC-N
├── 12-adr/                ADR-0001 ~ ADR-N
├── 13-testing/            Performance / Security / LLM Eval / Chaos / Benchmark
├── 14-release/            版本发布策略 / Release Notes / Changelog 规范
└── 15-roadmap/            版本路线图 / 长期规划
```

---

## 完整阶段流程总览

```
Phase -1    Project Governance            治理规则 / 工程原则 / 发布策略
    │
Phase 0     Repository Bootstrap          README / CI / Website / 仓库骨架
    │
Phase 1     Architecture Baseline          PRD + SAD + SDD Baseline + 项目脚手架
    │
Phase 1.5   Technical Validation (Spike)   5 个技术验证，不通过不进入 MVP
    │
Phase 2     MVP Development               登录 → 知识库 → RAG → Chat → Agent
    │
Phase 3+    迭代开发（每个模块循环）        Connector / Workflow / BI / Governance / Marketplace
```

---

## Phase -1：Project Governance

**目标**：先建立治理规则，再建仓库。

**代码量**：0%。

| 文档 | 内容 |
|---|---|
| **Engineering Principles** | 10 条工程原则（见上文） |
| **Maintainers** | 维护者列表、职责定义、晋升机制 |
| **Committers** | 提交者权限范围、提名流程 |
| **Release Policy** | 版本发布策略、LTS 定义、alpha/beta/rc 流程 |
| **Version Policy** | SemVer 细则、Breaking Change 定义 |
| **Branch Strategy** | Trunk-Based Development 详细规范 |
| **Coding Standard** | Java (Checkstyle+Spotless) / Python (Ruff) / Frontend (ESLint+Prettier) |
| **RFC Process** | Proposal → Discussion → Decision → Implementation |
| **ADR Process** | Title → Context → Decision → Consequences |
| **Security Policy** | 漏洞报告渠道、响应 SLA、披露流程 |
| **Community** | GitHub Discussion / Issue / 行为准则 |

**交付物**：`docs/00-governance/` 下全部治理文档。

---

## Phase 0：Repository Bootstrap

**目标**：建立 Apache 风格的成熟开源仓库。

**代码量**：0%（仅配置和模板，无业务代码）。

### 仓库基础文件

| 文件 | 说明 |
|---|---|
| `README.md` | 项目介绍、特性列表、快速开始 |
| `LICENSE` | Apache 2.0 |
| `SECURITY.md` | 安全漏洞报告方式 |
| `CONTRIBUTING.md` | 贡献流程 |
| `CODE_OF_CONDUCT.md` | 行为准则 |
| `ROADMAP.md` | 版本路线图 |
| `CHANGELOG.md` | 变更日志 |
| `CODEOWNERS` | 代码所有者 |
| `.editorconfig` | 编辑器统一配置 |

### GitHub 自动化

| 配置 | 说明 |
|---|---|
| `.github/ISSUE_TEMPLATE/` | Bug Report / Feature Request / RFC |
| `.github/PULL_REQUEST_TEMPLATE.md` | PR Checklist 模板 |
| `.github/workflows/ci.yml` | CI 基线流水线 |
| Dependabot / Renovate | 依赖自动更新 |
| Pre-commit hooks | 提交前自动检查 |

### 代码风格工具

| 技术栈 | Lint | Format |
|---|---|---|
| **Java** | Checkstyle + SpotBugs | Spotless |
| **Python** | Ruff | Ruff |
| **Frontend** | ESLint | Prettier |

### 目录结构（一次性初始化）

```
OpenEIP/
├── docs/           ← 00-governance ~ 15-roadmap
├── java/           ← Spring Boot 占位
├── python/         ← FastAPI 占位
├── frontend/       ← React + Vite 占位
├── deploy/         ← Docker / K8S / Helm 占位
├── sdk/            ← Java / Python / Go / JS SDK 目录
├── plugins/        ← 插件目录
├── benchmark/      ← 性能基准
├── website/        ← Docusaurus 官网
├── examples/       ← 示例项目
├── scripts/        ← 工具脚本
└── tools/          ← 辅助工具
```

### Branding

- **名称**：OpenEIP — Open Enterprise Intelligence Platform
- **Slogan**：AI Native Enterprise Platform
- **Logo**：待设计

**交付物**：一个像 Apache 项目的仓库。

---

## Phase 1：Architecture Baseline

**目标**：完成三份核心基线文档 + 可运行的脚手架。

**代码量**：≈5%（脚手架代码，非业务代码）。

### 三份基线文档

| 文档 | 回答的问题 | 篇幅 |
|---|---|---|
| **PRD**（产品需求文档） | 明确范围、用户、需求 ID、优先级、验收标准、非功能要求和路线假设 | 以可验收性为准 |
| **SAD**（软件架构设计） | 明确模块边界、数据所有权、通信、部署、安全、可观测性和未验证假设 | 以决策完整性为准 |
| **SDD Baseline**（设计基线） | 明确包/API/数据库/事件/SPI 契约和兼容规则 | 以契约可执行性为准 |

### SDD Baseline 范围界定

**定义**（全局通用规范）：

- 模块划分与目录规范
- 包结构（Java DDD 分层 / Python 分层）
- API 风格（REST / SSE / WebSocket / 分页 / 错误码）
- 数据库命名规范 / Migration 规范
- 事件模型（Kafka Topic 命名 / Event Schema）
- Plugin SPI / Connector SPI
- Agent 架构总览
- 编码规范与命名约定

**不定义**（留给各模块自己的子 SDD）：

- Knowledge 详细设计
- Workflow 详细设计
- Connector 详细设计
- 具体 Agent 设计

### 项目脚手架（同步搭建）

```
java/           → Spring Boot 多模块骨架，可编译
python/         → FastAPI 空服务，可启动
frontend/       → React + Vite 空壳，可启动
deploy/         → Docker Compose（空服务占位）
sdk/            → 目录结构
```

**交付物**：三份基线文档 + 可运行的空壳项目。

---

## Phase 1.5：Technical Validation (Spike)

**目标**：验证 SAD 中的关键技术假设，通过 Spike 消除风险。

**原则**：不通过验证，不进入 MVP。

| Spike | 验证内容 | 产出 |
|---|---|---|
| **Spike-001** | Java (Spring Boot) → gRPC → Python (FastAPI) → Streaming | 可工作的 RPC 通信原型 + 延迟/吞吐数据 |
| **Spike-002** | Kafka 事件驱动：Producer → Topic → Consumer 完整链路 | 事件模型可行性 + 吞吐基准 |
| **Spike-003** | Milvus 向量存储：Embedding → Insert → Search 全流程 | 检索性能数据 + 索引策略 |
| **Spike-004** | MCP Runtime 设计可行性验证 | MCP 协议可行性结论 |
| **Spike-005** | LLM Streaming：Gateway → Python → SSE → Frontend 端到端 | Streaming 链路延迟 + 断流恢复方案 |

### Spike 产出格式

```
Spike-XXX/
├── README.md       # 目标、方法、结论
├── code/           # 验证代码
└── report.md       # 详细报告 + 测试数据 + 决策建议
```

### 决策出口

| 结论 | 含义 |
|---|---|
| ✅ **通过** | 进入 MVP |
| ⚠️ **有条件通过** | 调整 SAD 后进入 MVP |
| ❌ **不通过** | 重新设计架构 |

**交付物**：5 份 Spike 验证报告 + ADR 决策记录。

---

## Phase 2 ~ N：迭代开发循环

**核心原则**：每个模块走同一流程。不等所有文档完成才开发。文档同步更新，非事后补文档。

### 单模块完整节奏

```
                         Issue
                           │
                           ▼
                     RFC（重大功能）
                           │
                           ▼
                     ADR（架构变更）
                           │
                           ▼
                      Module Design
                           │
                           ▼
                  API / DB / UI Design
                           │
                           ▼
                   Architecture Review   ← 检查是否符合 SAD / SDD / SPI
                           │
                           ▼
                      Implementation
                           │
                           ▼
                    Unit Test
                           │
                           ▼
                  Integration Test
                           │
                           ▼
                      Benchmark
                           │
                           ▼
                   Security Review       ← Prompt Injection / RBAC / Token / Secret
                           │
                           ▼
                     Quality Gate        ← 6 项强制门禁，全部通过才能 PR
                           │
                           ▼
                 Documentation Update    ← 同步更新，非补文档
                           │
                           ▼
                    Pull Request
                           │
                           ▼
                     Code Review
                           │
                           ▼
                        Merge
                           │
                           ▼
                       Release
```

### 每一步详解

| # | 步骤 | 说明 | 必须？ |
|---|---|---|---|
| 1 | **Issue** | GitHub Issue，明确要做什么、验收标准 | ✅ 必须 |
| 2 | **RFC** | 新模块 / 架构变更 / 破坏性改动 / 新 SPI | ⚠️ 按需 |
| 3 | **ADR** | 记录关键架构决策："为什么这样做" | ⚠️ 按需 |
| 4 | **Module Design** | 模块职责、边界、依赖关系、核心流程 | ✅ 必须 |
| 5 | **API / DB / UI Design** | 接口定义 / ER 图 / 页面结构 | ✅ 必须 |
| 6 | **Architecture Review** | 检查是否符合 SAD / SDD / SPI 规范 | ✅ 必须 |
| 7 | **Implementation** | Java / Python / Frontend 编码实现 | ✅ 必须 |
| 8 | **Unit Test** | 单元测试，覆盖率 ≥ 80% | ✅ 必须 |
| 9 | **Integration Test** | 集成测试 + Contract Test | ✅ 必须 |
| 10 | **Benchmark** | RAG QPS / OCR 速度 / Embedding 延迟 / Agent 响应时间 | ✅ 必须 |
| 11 | **Security Review** | Prompt Injection / RBAC / Token / Secret / 权限越权 | ✅ 必须 |
| 12 | **Quality Gate** | 6 项条件全部满足才能进入 PR（详见下文） | ✅ 必须 |
| 13 | **Docs Update** | 更新 API 文档 / SAD / PRD / README，**同步而非重写** | ✅ 必须 |
| 14 | **Pull Request** | 提交 PR，附带 Checklist | ✅ 必须 |
| 15 | **Code Review** | ≥ 1 人 Approve，检查设计合规 + 代码质量 + 测试覆盖 | ✅ 必须 |
| 16 | **Merge** | Squash / Rebase → main | ✅ 必须 |
| 17 | **Release** | 里程碑节点触发 alpha → beta → rc → stable | ✅ 里程碑 |

---

## Architecture Review（架构审查）

**定位**：Module Design 之后、Implementation 之前的强制审查节点。

**目标**：确保每个模块的设计不偏离全局架构基线。

**审查清单**：

- [ ] 模块边界是否符合 SAD 定义？
- [ ] API 风格是否符合 SDD 规范？
- [ ] 数据库设计是否符合命名 / 索引 / Migration 规范？
- [ ] Plugin / Connector 是否遵循 SPI 契约？
- [ ] 事件模型是否符合 Kafka Topic 规范？
- [ ] 权限模型是否符合 RBAC / ABAC 设计？
- [ ] 是否引入未经 ADR 记录的新技术依赖？

**审查通过 → 才能开始 Coding。**

---

## Quality Gate（质量门禁）

**定位**：Implementation → Testing → Benchmark → Security Review 全部完成后，进入 PR 之前的强制检查节点。

**6 项条件全部满足才能提交 PR：**

| # | 条件 | 标准 |
|---|---|---|
| 1 | **Test Coverage** | ≥ 80%，Unit + Integration |
| 2 | **Static Analysis** | Checkstyle / SpotBugs / SonarQube 零严重问题 |
| 3 | **Benchmark** | 对比基线无性能退化，新增指标已记录 |
| 4 | **Security Scan** | 无 HIGH / CRITICAL 级别漏洞 |
| 5 | **API Documentation** | OpenAPI / 接口文档已同步更新 |
| 6 | **Compatibility Check** | SDK 兼容 / API 无 Breaking Change / DB Migration 兼容 / Plugin SPI 兼容 |

### 第 6 项详细说明

| 检查维度 | 内容 |
|---|---|
| **SDK 兼容** | Java / Python / Go / JS SDK 签名不变，行为不变 |
| **API 兼容** | 无 Breaking Change（新增字段允许，删除/改名/改类型禁止） |
| **DB Migration 兼容** | 新 Migration 可正向执行 + 可回滚，不破坏存量数据 |
| **Plugin SPI 兼容** | SPI 接口不变，已有插件不重新编译即可运行 |

**任何一项不通过 → 不得进入 PR。**

---

## Release Train（版本列车）

### 单版本发布节奏

```
v0.5.0-alpha    ← 内部验证，功能可能不完整
      ↓
v0.5.0-beta     ← 功能冻结，开放社区测试
      ↓
v0.5.0-rc1      ← 候选发布，只修 Bug，不新增功能
      ↓
v0.5.0          ← 正式发布
```

### 完整版本路线

```
v0.1.0-alpha → beta → rc1 → v0.1.0    Foundation
                                    仓库 / 官网 / CI / 三份基线 / 脚手架

v0.2.0-alpha → beta → rc1 → v0.2.0    MVP
                                    登录 / RBAC / 文件上传 / OCR / 知识库 / RAG / Chat / Agent

v0.3.0-alpha → beta → rc1 → v0.3.0    Knowledge
                                    文档解析 / Embedding / 全文检索 / 向量检索 / Citation

v0.4.0-alpha → beta → rc1 → v0.4.0    Workflow
                                    Node / Canvas / Execution / Trigger / Approval / Retry

v0.5.0-alpha → beta → rc1 → v0.5.0    Connector
                                    MySQL / PostgreSQL / Kafka / Git / 飞书 / 企业微信 / Email

v0.6.0-alpha → beta → rc1 → v0.6.0    Agent
                                    Tool / Memory / Planner / Multi-Agent / MCP / Evaluation

v0.7.0-alpha → beta → rc1 → v0.7.0    Governance
                                    多租户 / 审计 / 模型管理 / Prompt 管理 / 成本 / Trace

v0.8.0-alpha → beta → rc1 → v0.8.0    Marketplace
                                    Plugin / SDK / Connector Market / Agent Market

v0.9.0-alpha → beta → rc1 → v0.9.0    Performance
                                    压测 / 调优 / 高可用 / 扩容 / 稳定性

v1.0.0-alpha → beta → rc1 → v1.0.0    LTS
                                    SSO / LDAP / K8S / 对象存储 / 安全合规 / 24 个月支持
```

### LTS 策略

- 每年发布一个 LTS 版本
- LTS 版本提供 **24 个月**长期支持（安全补丁 + 关键 Bug 修复）
- 非 LTS 版本支持至下一个版本发布后 3 个月

---

## 文档与代码的节奏

```
Phase -1          文档 100% ┃ 代码 0%
Phase 0           文档 80%  ┃ 代码 0%
Phase 1           文档 60%  ┃ 代码 5%（脚手架）
Phase 1.5         文档 10%  ┃ 代码 30%（Spike 验证）
Phase 2+          文档 🔄   ┃ 代码 ✅

🔄 = 随模块开发同步更新，不等全部完成
```

**三条铁律**：

1. PRD / SAD / SDD **永远不会"最终完成"**，它们随版本持续演进，每个版本都有对应版本号
2. **不等全部文档写完才写代码**，但每个模块在写代码之前必须有该模块的设计文档并通过 Architecture Review
3. **文档是同步更新**——Code 完成 → Test 通过 → Docs 同步，不是在最后"补文档"

---

## 工程治理规范总表

| 规范 | 内容 |
|---|---|
| **分支策略** | Trunk-Based Development（main + short-lived feature branch） |
| **Commit 规范** | Conventional Commits（`feat:` / `fix:` / `docs:` / `refactor:` / `test:` / `chore:`） |
| **版本规范** | Semantic Versioning（`MAJOR.MINOR.PATCH`） |
| **发布流程** | alpha → beta → rc1 → stable |
| **Issue 模板** | Bug Report / Feature Request / RFC 三类 |
| **PR 模板** | Description + Checklist + Screenshots + Breaking Change 标记 |
| **RFC 流程** | Proposal → Discussion → Decision → Implementation |
| **ADR 流程** | Title → Context → Decision → Consequences |
| **Architecture Review** | 每个模块 Design 后、Code 前强制审查 |
| **Quality Gate** | 6 项强制门禁（Coverage + Static Analysis + Benchmark + Security + Docs + Compatibility） |
| **Code Review** | ≥ 1 人 Approve，检查设计合规 + 代码质量 + 测试覆盖 |
| **文档规范** | Docs as Code / Markdown；中文为 Foundation 规范源，稳定版前补齐英文同步版 |
| **代码风格** | Checkstyle + Spotless + SpotBugs (Java) / Ruff (Python) / ESLint + Prettier (Frontend) |
| **测试覆盖率** | ≥ 80%（Unit + Integration） |
| **LTS 策略** | 每年一个 LTS，24 个月支持 |

---

## v1.0 LTS 最终目标全景

| 维度 | 能力 |
|---|---|
| **企业平台** | 多租户、RBAC/ABAC、组织管理、审计日志、模型治理、合规 |
| **AI 能力** | RAG、Agent（Planner/Executor/Reflection）、Workflow 引擎、Memory、Tool Calling、MCP 协议、多模型接入 |
| **数据知识** | 文档解析（OCR/PDF/Word/Excel）、全文检索、向量检索、知识图谱、30+ 企业连接器 |
| **开放生态** | Plugin Marketplace、Connector Marketplace、Agent Marketplace、Java/Python/Go/JS SDK、标准 REST API、Docusaurus 文档站 |
| **工程能力** | Docker Compose 一键部署、K8S Helm Chart、CI/CD（GitHub Actions）、Prometheus + Grafana 监控、OpenTelemetry 链路追踪、公开 Benchmark |
| **开源社区** | RFC 公开讨论、ADR 决策透明、Contributing Guide、LTS 策略、GitHub Discussion、版本路线图 |

---

## 最终流程全景图（一张图总结）

```
                    ┌─────────────────────────────┐
                    │   OpenEIP Engineering        │
                    │   Principles (10条)          │
                    │   永久不变                    │
                    └─────────────┬───────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Phase -1  →  Phase 0  →  Phase 1  →  Phase 1.5  →  Phase 2+  │
│                                                                  │
│  Governance   Bootstrap   Baseline    Technical     MVP → 迭代   │
│  治理规则     仓库骨架     PRD+SAD      Validation   开发循环     │
│  工程原则     CI/Website   +SDD基线     (5 Spikes)              │
│  发布策略     模板/工具    +脚手架       验证通过                 │
│              代码风格                  才进MVP                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                      单模块开发循环                               │
│                                                                  │
│  Issue → RFC → ADR → Module Design → API/DB/UI Design           │
│                                                ↓                 │
│                                         Architecture Review      │
│                                                ↓                 │
│                                          Implementation          │
│                                                ↓                 │
│                                   Unit Test → Integration Test   │
│                                                ↓                 │
│                                            Benchmark             │
│                                                ↓                 │
│                                         Security Review          │
│                                                ↓                 │
│                        ┌──────────────────────────────────────┐ │
│                        │         Quality Gate (6项)           │ │
│                        │                                      │ │
│                        │  ① Test Coverage ≥ 80%               │ │
│                        │  ② Static Analysis 零严重问题        │ │
│                        │  ③ Benchmark 无性能退化              │ │
│                        │  ④ Security Scan 无 HIGH/CRITICAL   │ │
│                        │  ⑤ API Docs 已同步更新               │ │
│                        │  ⑥ Compatibility Check 全部兼容      │ │
│                        └────────────────┬─────────────────────┘ │
│                                         ↓                       │
│                                  Documentation Update            │
│                                         ↓                       │
│                              PR → Code Review → Merge            │
│                                         ↓                       │
│                          Release (alpha→beta→rc1→stable)         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Release Train                             │
│                                                                  │
│  v0.1 → v0.2 → v0.3 → v0.4 → v0.5 → v0.6 → v0.7 → v0.8 →      │
│  Foundation  MVP   Know-  Work-  Conn-  Agent  Gover- Market-   │
│                    ledge  flow   ector         nance  place     │
│                                                                  │
│                                      v0.9 → v1.0 LTS            │
│                                      Perf-   Enterprise          │
│                                      ormance Ready               │
└─────────────────────────────────────────────────────────────────┘
```
