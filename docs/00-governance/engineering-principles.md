# OpenEIP Engineering Principles（工程原则）

> 所有开发、设计、Review 必须遵守的十条工程原则。这是整个项目最高的工程准则，永久有效。

---

## 原则 1：Documentation First

**文档先行，代码依据文档实现。**

- 任何模块在写代码之前，必须先有设计文档
- 设计文档通过 Architecture Review 后方可编码
- 文档与代码同步更新，不允许"先写代码、后补文档"
- PRD、SAD、SDD 作为整个项目的唯一设计基线

## 原则 2：Design Before Implementation

**先设计再编码，不边写边改。**

- Module Design → API Design → Database Design → 然后才是 Implementation
- 禁止在没有设计文档的情况下直接写代码
- 设计变更必须先更新文档，再修改代码
- RFC 机制确保重大变更有充分的讨论和评审

## 原则 3：API First

**先定义 API 契约，后实现。**

- 所有模块对外接口先定义 OpenAPI 规范
- REST / SSE / WebSocket 统一风格
- 错误码、分页、认证方式全局统一
- API 向后兼容——Breaking Change 必须经过 RFC

## 原则 4：Plugin First

**所有扩展点通过 SPI/SDK，不做硬编码。**

- Connector、Agent、Workflow Node 全部插件化
- 核心平台只提供框架，能力由插件提供
- Plugin SPI 稳定且向后兼容
- Marketplace 生态依赖 Plugin First 原则

## 原则 5：Event Driven

**模块间通过事件通信，松耦合。**

- Java Platform 和 Python AI Engine 之间通过 Kafka 事件解耦
- 事件 Schema 全局统一管理
- 支持事件溯源和重放
- 同步调用（REST/gRPC）和异步事件明确分工

## 原则 6：AI Native

**AI 不是外挂功能，是平台原生能力。**

- Agent、RAG、Workflow、Memory 是平台一等公民
- 所有模块设计时默认考虑 AI 集成
- MCP 协议作为 AI 工具调用的标准接口
- LLM 可观测性（Trace、Token、Cost）内建

## 原则 7：Everything Observable

**所有组件可监控、可追踪、可度量。**

- Prometheus + Grafana 作为监控基线
- OpenTelemetry 全链路追踪
- LLM 调用链路透明可追溯
- Benchmark 公开且自动化运行
- 日志、指标、链路三者统一

## 原则 8：Security by Default

**安全不是事后补丁，是默认配置。**

- RBAC/ABAC 权限模型内建
- JWT / OAuth2 / OIDC 认证标准
- Prompt Injection 防护内置
- Secret 管理、加密、脱敏作为基础设施
- Security Review 是 Quality Gate 必选项

## 原则 9：Community Driven

**RFC 公开讨论，决策透明。**

- 重大功能通过 RFC 流程公开讨论
- 架构决策通过 ADR 记录并公开
- Issue 和 Discussion 对社区开放
- 维护者和提交者的晋升机制透明

## 原则 10：Backward Compatibility

**API / SDK / SPI 向后兼容。**

- Breaking Change 必须经过 RFC
- 语义化版本明确标注兼容性变更
- Compatibility Check 是 Quality Gate 必选项
- SDK 签名和行为在同一个大版本内不变
- 数据库 Migration 可正向执行且可回滚

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1.0 | 2026-07-20 | 初始版本，定义十条工程原则 |
