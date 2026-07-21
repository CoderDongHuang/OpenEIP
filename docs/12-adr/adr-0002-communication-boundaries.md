# ADR-0002：跨运行时通信边界

## Status

Accepted

## Date

2026-07-20

## Context

Java 与 Python 同时需要普通请求、流式响应和长任务通知。直接选定 REST、gRPC 和 Kafka 的组合但没有测量数据，会固化未经验证的复杂度。

## Decision

Foundation 继续只使用 HTTP 健康端点。Phase 2 起按以下边界引入通信能力：

| 场景 | 通道 | 约束 |
|---|---|---|
| 常规同步 AI 调用 | REST HTTP | 短请求/响应；遵循统一错误信封 |
| Java → Python 内部实时调用 | gRPC Streaming | 共享 Protobuf；必须设置 deadline、错误映射和可观测性 |
| 长任务与模块事件 | Kafka | 至少一次投递；`eventId` 幂等；失败 3 次进入 DLQ |
| Gateway → Browser 流式输出 | SSE | Nginx 关闭缓冲；定义 token/done/error；支持客户端取消 |

验证依据：

- [Spike-001](../../spike/spike-001-grpc-java-python/report.md)：跨语言 gRPC unary/streaming/error 全部通过。
- [Spike-002](../../spike/spike-002-kafka-eventing/report.md)：真实 Kafka Producer/Consumer、去重和 DLQ 回读通过。
- [Spike-005](../../spike/spike-005-llm-streaming/report.md)：真实 Chromium 经 Nginx 的 SSE、错误、取消和重连通过。

## Consequences

### Positive

- 在稳定 API 前保留调整空间。
- 决策建立在可复现数据上。

### Negative

- Phase 1.5 前不能承诺生产通信契约。

### Risks

- Spike 范围过窄可能低估生产复杂度；报告必须包含失败恢复和追踪。

## Alternatives Considered

| 方案 | 优点 | 缺点 | 为何不选 |
|---|---|---|---|
| 仅 REST/SSE | 简单 | 双向流和强契约能力有限 | 保留为候选 |
| 全 gRPC | 强契约 | 浏览器与调试成本 | 保留内部场景 |
| 所有操作走事件 | 高解耦 | 请求响应语义复杂 | 不适合短调用 |
