# Contributing to OpenEIP

感谢你对 OpenEIP 的关注！我们欢迎任何形式的贡献。

## 开始之前

1. 阅读 [OEP.md](OEP.md) 了解项目研发流程
2. 阅读 [docs/00-governance/engineering-principles.md](docs/00-governance/engineering-principles.md) 了解工程原则
3. 阅读 [docs/00-governance/coding-standard.md](docs/00-governance/coding-standard.md) 了解编码规范

## 贡献方式

### 报告 Bug

1. 使用 GitHub Issues 创建 Bug Report
2. 描述复现步骤、预期行为和实际行为
3. 提供环境信息（OS、版本等）

### 提出功能

1. 使用 GitHub Issues 创建 Feature Request
2. 描述需求和预期行为
3. 重大功能将通过 RFC 流程讨论

### 代码贡献

1. Fork 仓库
2. 创建 feature 分支：`feature/<issue-id>-<description>`
3. 遵循 [Conventional Commits](docs/00-governance/branch-strategy.md)
4. 确保所有 Quality Gate 通过
5. 提交 Pull Request

### 文档贡献

文档同样重要！包括：

- 修复文档错误
- 补充教程和示例
- 翻译（中英文同步）

## 开发流程

每个模块的开发遵循：

```
Issue → RFC（如需要）→ Module Design → API/DB/UI Design
    → Architecture Review → Implementation → Testing
    → Benchmark → Security Review → Quality Gate
    → Docs Update → PR → Code Review → Merge
```

详见 [OEP.md](OEP.md)

## Quality Gate

提交 PR 前必须满足：

1. Test Coverage ≥ 80%
2. Static Analysis 零严重问题
3. Benchmark 无性能退化
4. Security Scan 无 HIGH/CRITICAL
5. API Docs 已同步更新
6. Compatibility Check 全部兼容

## Code of Conduct

所有参与者必须遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。
