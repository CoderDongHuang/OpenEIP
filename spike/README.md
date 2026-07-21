# Phase 1.5 Technical Validation

本目录包含 OEP Phase 1.5 的 5 个可复现技术验证。每个 Spike 均使用固定版本依赖、Docker Compose 隔离运行、自动验收，并将机器可读证据保存到各自的 `results/` 目录。

## 运行要求

- Docker Desktop 4.40+（Linux Containers）
- Docker Compose v2 或 v5
- PowerShell 5.1+
- 首次运行可用磁盘空间约 8 GB（主要用于 Milvus 和 Playwright 镜像）

不需要 OpenAI API Key。Spike-003 和 Spike-005 使用本地确定性 OpenAI-compatible fixture，验证协议和链路；真实模型质量与公网服务延迟不在本阶段结论范围内。

## 执行

在仓库根目录运行：

```powershell
./spike/run-all.ps1
```

也可以进入任一 Spike 目录单独执行 `./run.ps1`。脚本会在成功或失败后清理该 Spike 的容器和匿名卷，并保留 `results/*.json`。

## 验收出口

| Spike | 验证范围 | 原始证据 |
|---|---|---|
| [Spike-001](spike-001-grpc-java-python/README.md) | Java 21 ↔ Python 3.12 gRPC + Streaming | [`result.json`](spike-001-grpc-java-python/results/result.json) |
| [Spike-002](spike-002-kafka-eventing/README.md) | Java Producer → Kafka → Python Consumer + DLQ | [`result.json`](spike-002-kafka-eventing/results/result.json) |
| [Spike-003](spike-003-milvus-vector/README.md) | Embedding → Milvus Insert → Search | [`result.json`](spike-003-milvus-vector/results/result.json) |
| [Spike-004](spike-004-mcp-runtime/README.md) | 官方 MCP SDK 初始化、发现与调用 | [`result.json`](spike-004-mcp-runtime/results/result.json) |
| [Spike-005](spike-005-llm-streaming/README.md) | Chromium → Gateway → Python → LLM-compatible → SSE | [`result.json`](spike-005-llm-streaming/results/result.json) |

5 个 `result.json` 的顶层 `passed` 必须全部为 `true`，才能接受 ADR-0004 并进入 Phase 2。
