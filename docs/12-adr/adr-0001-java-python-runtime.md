# ADR-0001：Java Platform 与 Python AI Engine

## Status

Accepted

## Date

2026-07-20

## Context

平台同时需要企业事务、权限治理和 AI/ML 生态。单一语言会让其中一侧承担持续适配成本。

## Decision

Java 21 + Spring Boot 负责领域控制面和事务边界；Python 3.12 + FastAPI 负责无状态 AI 执行。跨运行时契约必须语言无关，不共享数据库内部模型。

Phase 1.5 的 [Spike-001](../../spike/spike-001-grpc-java-python/report.md) 已验证 Java 21 与 Python 3.12 可通过生成的 Protobuf 契约完成 unary、服务端流、双向流和错误传播。该结果确认互操作性，不改变双方的数据所有权边界。

## Consequences

### Positive

- 保留 Java 企业生态和 Python AI 生态优势。
- 允许两个运行时独立扩展和部署。

### Negative

- 增加跨语言契约、追踪和部署成本。
- 团队需要维护两套工具链。

### Risks

- 边界失控会形成分布式单体；通过契约测试和明确数据所有权缓解。

## Alternatives Considered

| 方案 | 优点 | 缺点 | 为何不选 |
|---|---|---|---|
| 全 Python | AI 生态直接 | 企业事务和 Java 集成目标不足 | 不符合定位 |
| 全 Java | 工具链统一 | AI 依赖适配成本较高 | 不符合定位 |
