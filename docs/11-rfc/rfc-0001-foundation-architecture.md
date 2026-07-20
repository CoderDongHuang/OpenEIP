# RFC-0001：Foundation Architecture

## Status

Accepted (Bootstrap)

## Abstract

建立 OpenEIP `v0.1.0-alpha` 的仓库边界、运行时边界、质量门禁和后续技术验证出口。

## Motivation

项目需要先形成可构建、可启动、可审查的最小基础，再验证 AI、事件总线、向量检索和插件运行时。提前把路线图能力放入默认部署会增加维护成本，也会制造并不存在的成熟度。

## Design

- Java 21 + Spring Boot 提供企业领域控制面骨架。
- Python 3.12 + FastAPI 提供 AI 执行面骨架。
- React + Vite 提供前端壳，Docusaurus 提供文档站。
- Foundation Compose 只运行 Gateway、Java、Python 和 Frontend。
- Kafka、Milvus、LLM Streaming 和 MCP Runtime 进入 Phase 1.5 Spike。
- 公开契约以语言无关 Schema 为源，生成 Java/Python 绑定。
- CI 必须验证构建、测试、80% 覆盖率、格式、Compose 和 HIGH/CRITICAL 安全问题。

## Alternatives Considered

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| 一开始部署全部基础设施 | 架构图完整 | 启动重、未验证、维护面过大 | 拒绝 |
| 单一 Python 运行时 | AI 开发直接 | 企业事务与 Java 生态目标不足 | 暂不采用 |
| 单一 Java 运行时 | 企业工程统一 | AI/ML 生态接入成本高 | 暂不采用 |

## Impact

- API：Foundation 只承诺健康与信息端点。
- SDK/SPI：只形成设计草案，未进入稳定兼容承诺。
- Database：Foundation 不创建业务 Schema。
- Security：无生产默认 Secret；CI 阻止 HIGH/CRITICAL 问题。

## Migration Plan

后续 Spike 改变本 RFC 时，通过新 RFC 和 ADR 记录；`0.x` Breaking Change 按版本策略递增 MINOR 并提供迁移说明。

## Decision Record

本 RFC 在公共社区形成前由 Bootstrap Maintainer 接受。自仓库首次公开发布起，后续 RFC 执行标准讨论与投票周期。
