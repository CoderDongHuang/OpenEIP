# Spike-001：Java ↔ Python gRPC + Streaming

## 目标

验证 Java 21 客户端通过共享 Protobuf 契约调用 Python 3.12 gRPC 服务，并验证 unary、服务端流、双向流和错误传播。

## 验收标准

| 验证项 | 标准 |
|---|---|
| Unary | 20 次预热后测量 200 次；P99 < 50ms；响应内容断言通过 |
| Server Streaming | 首事件 < 100ms；流正常完成 |
| Bidirectional Streaming | 连续发送 3 轮并收到 3 个对应完成事件 |
| Error Propagation | Python `INVALID_ARGUMENT` 在 Java 观察为同一状态码 |

## 运行

```powershell
./run.ps1
```

Compose 会在构建时生成双方代码、执行 Java `spotlessCheck`，启动 Python Server，再运行 Java Client。成功证据写入 [`results/result.json`](results/result.json)。

## 文件

```text
proto/agent.proto
python-server/server.py
java-client/GrpcClient.java
build.gradle.kts
Dockerfile.python
Dockerfile.java
compose.yaml
run.ps1
results/result.json
```

详细实测结论见 [report.md](report.md)。
