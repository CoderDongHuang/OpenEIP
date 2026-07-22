# Release Policy（版本发布策略）

> 本文档定义 OpenEIP 版本的发布策略、发布流程和 LTS 规范。

---

## 版本命名

遵循 Semantic Versioning 2.0.0：

```
MAJOR.MINOR.PATCH

MAJOR：不兼容的 API 变更（Breaking Change）
MINOR：向后兼容的功能新增
PATCH：向后兼容的 Bug 修复
```

## 发布阶段

计划晋升为稳定版的版本使用以下成熟度阶段：

```
alpha  →  beta  →  rc1  →  stable
```

推送 `v*` 标签后，Release 工作流必须先通过完整 CI，再发布 Java、Python、Frontend 镜像到 GHCR 并创建 GitHub Release。标签发布失败时不得手工跳过验证；修复后使用新标签重新发布。

预发布版本不保证晋升到下一阶段。Alpha 或 Beta 可以因范围调整被终止，或由下一 MINOR 版本列车承接；
终止决定必须写入 Roadmap、Changelog 和 Release 记录，已经发布的标签与产物不得删除、移动或重用。
`v0.1.0-alpha` Foundation 已由 `v0.2.0-alpha` MVP 承接，因此不追补缺少实际验证意义的
`v0.1.0-beta`、`v0.1.0-rc1` 或 `v0.1.0` 标签。

### alpha

- **目的**：内部验证，快速迭代
- **功能可能不完整**，允许功能增减
- **仅内部测试**，不推荐外部使用
- **标签格式**：`v0.5.0-alpha`

### beta

- **目的**：功能冻结，开放社区测试
- **所有计划功能已完成**，不再增减
- **开放社区测试**，欢迎 Bug 反馈
- **标签格式**：`v0.5.0-beta`

### rc1 (Release Candidate)

- **目的**：候选发布，只修 Bug
- **功能完全冻结**
- **只接受 Bug 修复**
- **可多次发布 rc**（rc1, rc2, ...）
- **标签格式**：`v0.5.0-rc1`

### stable

- **目的**：正式发布，生产可用
- **所有已知 Bug 已修复**
- **标签格式**：`v0.5.0`

## Release Train（版本列车）

```
v0.1.0    Foundation          仓库 / 官网 / CI / 三份基线 / 脚手架
v0.2.0    MVP                 登录 / RBAC / 文件上传 / OCR / 知识库 / RAG / Chat / Agent
v0.3.0    Knowledge           文档解析 / Embedding / 全文检索 / 向量检索 / Citation
v0.4.0    Workflow            Node / Canvas / Execution / Trigger / Approval / Retry
v0.5.0    Connector           MySQL / PostgreSQL / Kafka / Git / 飞书 / 企业微信 / Email
v0.6.0    Agent               Tool / Memory / Planner / Multi-Agent / MCP / Evaluation
v0.7.0    Governance          多租户 / 审计 / 模型管理 / Prompt 管理 / 成本 / Trace
v0.8.0    Marketplace         Plugin / SDK / Connector Market / Agent Market
v0.9.0    Performance         压测 / 调优 / 高可用 / 扩容 / 稳定性
v1.0.0    LTS                 SSO / LDAP / K8S / 对象存储 / 安全合规 / 24 个月支持
```

## LTS 策略

### LTS 定义

- 每年发布一个 LTS（Long Term Support）版本
- v1.0.0 是首个 LTS 版本
- 后续 LTS 版本间隔约 12 个月（如 v2.0.0、v3.0.0）

### LTS 支持周期

- LTS 版本提供 **24 个月**长期支持
- 支持内容包括：安全补丁 + 关键 Bug 修复
- 非 LTS 版本支持至下一个版本发布后 3 个月

### LTS 升级保证

- LTS 到下一个 LTS 提供升级指南
- 跨 LTS 升级（如 v1.0 → v3.0）需经过中间 LTS（v2.0）

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1.1 | 2026-07-22 | 明确预发布版本可终止或由下一 MINOR 承接，禁止追补或重用标签 |
| v1.0 | 2026-07-20 | 初始版本，定义发布策略和 LTS 规范 |
