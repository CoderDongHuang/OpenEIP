# ADR Process（架构决策记录流程）

> 本文档定义 OpenEIP 的 ADR（Architecture Decision Record）流程——关键架构决策的记录标准。

---

## 什么情况需要 ADR

以下场景**必须**记录 ADR：

- 选择关键技术栈（如数据库、消息队列、向量存储）
- 选择架构模式（如事件驱动、CQRS、微服务拆分）
- 决定模块边界和通信协议
- 决定安全策略（认证方式、权限模型）
- 决定性能策略（缓存方案、索引策略）
- 任何被讨论过多个方案并最终选择的决策

以下场景**不需要** ADR：

- 日常编码选择（不影响架构）
- 已被 SAD/SDD 覆盖的标准实践
- 临时性决策（仅在单次 PR 内有效）

## ADR 格式

每个 ADR 放在 `docs/12-adr/` 目录下：

```
docs/12-adr/
├── adr-0001-why-java-and-python.md
├── adr-0002-why-event-driven.md
├── adr-0003-why-milvus.md
└── ...
```

### ADR 文档模板

```markdown
# ADR-0001：<标题>

## Status
Proposed | Accepted | Deprecated | Superseded by ADR-XXXX

## Date
YYYY-MM-DD

## Context
背景：当时面临什么问题？有哪些约束？

## Decision
决策：选择了什么方案？

## Consequences
后果：这个决策带来什么影响？

### Positive
- 好处 1
- 好处 2

### Negative
- 代价 1
- 代价 2

### Risks
- 风险 1 及缓解措施

## Alternatives Considered
| 方案 | 优点 | 缺点 | 为何不选 |
|---|---|---|---|
| 方案 A | ... | ... | ... |
| 方案 B | ... | ... | ... |
```

## ADR 生命周期

- **Proposed**：新创建的 ADR
- **Accepted**：已通过并生效的决策
- **Deprecated**：不再适用的决策（但仍保留供查阅）
- **Superseded**：被新的 ADR 替代（注明替代的 ADR 编号）

## ADR 编号规则

```
ADR-0001    ← 顺序编号，四位数字，永不重复
ADR-0002
...
```

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1.0 | 2026-07-20 | 初始版本 |
