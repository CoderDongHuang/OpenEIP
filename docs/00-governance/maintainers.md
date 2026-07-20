# Maintainers（维护者）

> 本文档定义 OpenEIP 项目的维护者列表、职责、权限和晋升机制。

---

## 维护者职责

维护者（Maintainer）是项目的核心管理者，承担以下职责：

- **代码审查**：Review 并 Approve Pull Request
- **架构决策**：参与 ADR 讨论和决策
- **RFC 评审**：对 RFC 提案进行技术评审和决策
- **版本发布**：负责 Release 流程的执行
- **社区治理**：维护 Issue/Discussion 秩序，处理违规行为
- **方向把控**：确保项目按照 Roadmap 和 Engineering Principles 演进

## 维护者权限

- 对紧急修复 PR 启用加速评审的权限；仍不得绕过分支保护
- Approve 和 Merge PR 的权限
- 创建和管理 RFC 的权限
- 触发 Release 流程的权限
- 管理 Issue 和 Discussion 的权限

## 当前维护者

| 姓名 | GitHub ID | 角色 | 关注领域 |
|---|---|---|---|
| CoderDongHuang | `@CoderDongHuang` | Bootstrap Maintainer | 总体架构与发布 |

在社区形成至少两名独立 Maintainer 前，`@CoderDongHuang` 作为 Bootstrap Maintainer 执行发布和治理职责。涉及治理规则变更时，必须在公开 RFC 中记录理由和异议；不得用 Bootstrap 身份绕过 CI、分支保护或安全规则。

## 晋升机制

### 从 Contributor 到 Committer

满足以下条件的 Contributor 可被提名为 Committer：

- 至少贡献 5 个被合并的 PR
- 至少参与 3 个 Issue 或 RFC 讨论
- 熟悉项目的 Engineering Principles 和开发流程
- 由至少 1 名 Maintainer 提名

### 从 Committer 到 Maintainer

满足以下条件的 Committer 可被提名为 Maintainer：

- 作为 Committer 活跃超过 6 个月
- 至少主导完成 1 个模块的设计和实现
- 至少 Review 过 20 个 PR
- 展现出对项目方向的深入理解和判断力
- 由至少 2 名 Maintainer 联合提名

### 提名流程

1. 提名人在 GitHub Discussion 中发起提名
2. 被提名人提供自我陈述
3. 现有 Maintainer 投票（≥ 2/3 赞成通过）
4. 投票期不少于 1 周
5. 通过后更新本文档

## 退出机制

- 维护者可以自愿退出
- 连续 6 个月无活跃贡献的 Maintainer 自动转为 Emeritus 状态
- 严重违反行为准则的 Maintainer 经投票可移除

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1.0 | 2026-07-20 | 初始版本 |
