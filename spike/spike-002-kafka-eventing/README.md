# Spike-002：Kafka Event Streaming

## 目标

验证 Java Producer → Apache Kafka 3.9.1 KRaft → Python Consumer 的完整链路，以及幂等去重、3 次重试和真实 DLQ Topic。

## 验收标准

| 验证项 | 标准 |
|---|---|
| Producer | 使用 `acks=all` 和 idempotent producer 发出 2003 条记录 |
| Consumer | 在 90 秒内消费全部 2003 条记录并提交 offset |
| Idempotency | 两条相同 `eventId` 只处理一次，重复计数为 1 |
| DLQ | 毒消息尝试 3 次后写入 DLQ，并由独立 Consumer 回读验证 |

## 运行

```powershell
./run.ps1
```

成功后生成 [`results/producer.json`](results/producer.json) 和 [`results/result.json`](results/result.json)。内存 `set` 仅用于本 Spike 验证，生产实现必须使用可持久化幂等存储。

详细实测结论见 [report.md](report.md)。
