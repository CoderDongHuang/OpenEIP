# Spike-001 验证报告：Java ↔ Python gRPC + Streaming

## 概述

| 项目 | 值 |
|---|---|
| 执行时间 | 2026-07-21T10:12:04Z |
| 环境 | Docker Desktop Linux Containers；Java 21；Python 3.12；gRPC 1.71.0 |
| 原始证据 | [`results/result.json`](results/result.json) |
| 状态 | 通过 |

## 实测结果

| 验证项 | 验收标准 | 实际结果 | 判定 |
|---|---|---|---|
| Unary | 200 samples；P99 < 50ms | P50 1.656ms；P95 4.108ms；P99 5.763ms；495.50 RPS | PASS |
| Server Streaming | 首事件 < 100ms；正常完成 | 首事件 7.610ms；正常完成 | PASS |
| Bidirectional Streaming | 3 轮收发完整 | 发送 3 轮，收到 3 轮 | PASS |
| Error Propagation | `INVALID_ARGUMENT` 原样传播 | Java 观察到 `INVALID_ARGUMENT` | PASS |

## 结论与边界

Java 与 Python 通过共享 Protobuf 契约进行 unary 和 streaming gRPC 通信可行，ADR-0002 可接受该内部实时通信边界。

本结果来自单机 Docker 网络和确定性轻量处理，不代表跨主机、TLS、真实模型推理或生产负载。Phase 2 仍需补充 TLS、deadline、重试、健康检查、OpenTelemetry 和契约兼容测试。

## 决策

**通过**：允许进入 Phase 2 设计与实现。
