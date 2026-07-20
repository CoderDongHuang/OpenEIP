# ADR-0002：跨运行时通信边界

## Status

Proposed - pending Spike-001, Spike-002 and Spike-005

## Date

2026-07-20

## Context

Java 与 Python 同时需要普通请求、流式响应和长任务通知。直接选定 REST、gRPC 和 Kafka 的组合但没有测量数据，会固化未经验证的复杂度。

## Decision

Foundation 只使用 HTTP 健康端点。候选方案为 REST 处理普通请求、gRPC 处理内部流式调用、事件总线处理可重试长任务。最终边界由三个 Spike 的延迟、吞吐、恢复和运维数据决定。

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
