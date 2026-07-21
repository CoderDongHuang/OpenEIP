# ADR-0004：Phase 1.5 Spike 验证结论与后续决策

## Status

Accepted

## Date

2026-07-21

## Context

SAD Foundation Baseline 将跨运行时通信、事件总线、向量检索、MCP 和浏览器流式输出列为关键技术假设。进入 Phase 2 前，OEP 要求用可复现代码、原始数据和报告验证这些假设，不能依据未运行的示例代码直接接受架构。

## Decision

接受 Phase 1.5 的验证出口，允许进入 Phase 2。具体决策如下：

| Spike | 结论 | 决策影响 |
|---|---|---|
| [001](../../spike/spike-001-grpc-java-python/report.md) | 通过 | 接受 gRPC 作为 Java → Python 内部实时通信候选；Phase 2 补 deadline/TLS/OTel |
| [002](../../spike/spike-002-kafka-eventing/report.md) | 通过 | 接受 Kafka 至少一次投递 + 幂等 + 3 次重试 + DLQ；生产去重必须持久化 |
| [003](../../spike/spike-003-milvus-vector/report.md) | 有条件通过 | 保留 Milvus 为 Phase 3 候选；真实语料、容量和恢复验证前不进入默认 Profile |
| [004](../../spike/spike-004-mcp-runtime/report.md) | 通过 | 接受官方 MCP SDK；禁用自定义协议模拟作为兼容性证明 |
| [005](../../spike/spike-005-llm-streaming/report.md) | 通过 | 接受 Gateway → Browser SSE；必须关闭代理缓冲并处理错误/取消 |

机器可读原始证据位于每个 Spike 的 `results/` 目录，五个顶层 `passed` 均为 `true`。Spike-003 的 `passed` 表示本阶段验收标准满足，不取消其生产化前置条件。

## Consequences

### Positive

- 关键技术方向有可复现运行证据，不再依赖手写结论。
- 所有外部服务由 Compose 隔离，验证不需要外部 LLM 凭据。
- 延迟、吞吐、正确性和失败路径以 JSON 保存，可作为后续基线。

### Negative

- 首次运行需要下载 Milvus 和 Playwright 等大型镜像。
- 单机确定性数据不能外推到生产容量、真实模型质量或公网延迟。
- Spike 代码是验证资产，Phase 2 必须按模块 SDD 重新实现生产能力。

### Risks

- 供应链版本和运行环境变化可能改变结果；通过固定依赖版本、保存证据和 CI 静态门禁缓解。
- 本地单节点测试低估分布式故障；在相关模块进入发布范围前必须追加多节点、恢复与安全测试。

## Action Items

| # | 行动 | 阶段 |
|---|---|---|
| 1 | 固化 Protobuf 与 Event Schema 到 `contracts/`，增加兼容测试 | Phase 2 |
| 2 | 为 gRPC/SSE 补充 deadline、错误码、取消和 OpenTelemetry 规范 | Phase 2 |
| 3 | 持久化 Kafka 幂等键并增加 DLQ 监控 | Phase 2 |
| 4 | 定义 MCP 远程 Transport、认证、租户隔离和审计 | Phase 3 |
| 5 | 使用真实语料和目标 Embedding 模型复测 Milvus 容量与召回率 | Phase 3 |
