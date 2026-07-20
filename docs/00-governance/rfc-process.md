# RFC Process（RFC 流程）

> 本文档定义 OpenEIP 的 RFC（Request for Comments）流程——重大功能的提案、讨论、决策和实施标准。

---

## 什么情况需要 RFC

以下场景**必须**提交 RFC：

- 新增模块（如新 Connector、新 Agent 类型、新 Workflow 节点类型）
- 架构变更（如引入新技术栈、改变模块通信方式）
- 破坏性修改（Breaking Change to API / SDK / SPI）
- 新增 Plugin SPI 或修改已有 SPI
- 安全策略变更
- 版本策略变更

以下场景**不需要** RFC：

- Bug 修复
- 已有模块内的功能增强（不改变 API 契约）
- 文档更新
- 测试补充
- 性能优化（不改变 API 行为）

## RFC 流程

```
Proposal  →  Discussion  →  Decision  →  Implementation
   │              │             │              │
   │         ≥ 1 周讨论      维护者投票      按 RFC 实施
   │         Community       ≥ 2/3 通过      跟踪 Issue
```

### 1. Proposal（提案）

提案者在 `docs/11-rfc/` 下创建 RFC 文档：

```
docs/11-rfc/
└── rfc-0001-plugin-architecture.md
```

RFC 文档必须包含：

```markdown
# RFC-0001：<标题>

## Status
Proposed | Accepted | Rejected | Implemented

## Abstract
一句话概述

## Motivation
为什么要做？解决什么问题？

## Design
详细设计方案

## Alternatives Considered
考虑过的替代方案及为何不选

## Impact
- API：影响范围
- SDK：影响范围
- Plugin SPI：影响范围
- Database：是否有 Migration
- Security：安全影响

## Migration Plan
如有 Breaking Change，迁移方案是什么？

## References
相关链接
```

### 2. Discussion（讨论）

- RFC 提交后在 GitHub Discussion 中公告
- 讨论期**不少于 1 周**
- 社区成员、Committer、Maintainer 均可参与讨论
- 提案者负责回应和更新 RFC 文档

### 3. Decision（决策）

- 讨论结束后，Maintainer 进行投票
- **≥ 2/3 Maintainer 赞成**方可通过
- 投票结果和决策理由记录在 RFC 文档中
- 通过 → Status 更新为 `Accepted`
- 拒绝 → Status 更新为 `Rejected`，记录拒绝原因

### 4. Implementation（实施）

- Accepted 的 RFC 创建对应的 GitHub Issue
- 按照 OEP 标准流程实施：
  - Issue → Module Design → Architecture Review → Implementation → ...
- RFC 文档随实施进展更新（如发现设计调整）
- 完成后 Status 更新为 `Implemented`

## RFC 编号规则

```
RFC-0001    ← 顺序编号，四位数字，永不重复
RFC-0002
...
```

编号一旦分配，即使 RFC 被拒绝，也不会重新使用。

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1.0 | 2026-07-20 | 初始版本 |
