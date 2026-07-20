# Branch Strategy（分支策略）

> 本文档定义 OpenEIP 的 Git 分支策略、Commit 规范和合并流程。

---

## 分支模型：Trunk-Based Development

```
main
  │
  ├── feature/xxx        ← 功能开发（从 main 拉出，合并回 main）
  ├── fix/xxx            ← Bug 修复（从 main 拉出，合并回 main）
  ├── docs/xxx           ← 文档更新（从 main 拉出，合并回 main）
  └── release/x.y.z     ← 发布分支（从 main 拉出，用于发布准备）
```

### 分支类型

| 分支类型 | 命名格式 | 生命周期 | 说明 |
|---|---|---|---|
| **main** | `main` | 永久 | 主分支，始终保持可发布状态 |
| **feature** | `feature/<issue-id>-<description>` | 短期（< 1 周） | 功能开发分支 |
| **fix** | `fix/<issue-id>-<description>` | 短期（< 1 天） | Bug 修复分支 |
| **docs** | `docs/<description>` | 短期 | 纯文档更新分支 |
| **release** | `release/v<version>` | 短期（发布周期内） | 发布准备分支 |

### 分支命名示例

```
feature/42-knowledge-base
fix/87-rag-streaming-bug
docs/api-error-codes
release/v0.3.0
```

## Commit 规范：Conventional Commits

### 格式

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Type 类型

| Type | 说明 |
|---|---|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档更新 |
| `refactor` | 代码重构（不改变功能） |
| `test` | 测试相关 |
| `chore` | 构建/工具/依赖更新 |
| `perf` | 性能优化 |
| `security` | 安全修复 |

### Scope 范围

| Scope | 说明 |
|---|---|
| `java` | Java Platform 模块 |
| `python` | Python AI Engine 模块 |
| `frontend` | 前端模块 |
| `docs` | 文档 |
| `deploy` | 部署相关 |
| `sdk` | SDK 相关 |
| `ci` | CI/CD |

### 示例

```
feat(python): add document parsing pipeline

- Support PDF, Word, Excel parsing
- Add OCR integration
- Add chunking strategy

Closes #42
```

```
fix(java): resolve NPE in RBAC permission check

Fixes #87
```

```
security(python): fix prompt injection vulnerability in chat endpoint
```

## 合并策略

- **Feature/Fix → main**：Squash Merge
- **Release → main**：Merge Commit（保留发布历史）
- **禁止直接推送到 main**

## main 分支保护规则

- 禁止直接 Push
- 必须通过 PR
- 必须通过 CI（所有 Quality Gate）
- 必须至少 1 人 Code Review Approve
- 分支必须与 main 保持同步（需 Rebase 后才能 Merge）

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1.0 | 2026-07-20 | 初始版本 |
