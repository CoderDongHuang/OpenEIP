# Spike-002 验证报告：Kafka Event Streaming

## 概述

| 项目 | 值 |
|---|---|
| 执行时间 | 2026-07-21T10:14:15Z |
| 环境 | Apache Kafka 3.9.1 KRaft；Java 21；Python 3.12；aiokafka 0.12.0 |
| 原始证据 | [`results/producer.json`](results/producer.json)、[`results/result.json`](results/result.json) |
| 状态 | 通过 |

## 实测结果

| 验证项 | 实际结果 | 判定 |
|---|---|---|
| Producer → Topic | Java 发出 2003 条；`acks=all`；idempotence enabled；224.76 RPS | PASS |
| Consumer | Python 收到全部 2003 条；测量窗口 9675.46 RPS | PASS |
| 幂等 | 正常处理 2001 条，相同 `eventId` 重复跳过 1 条 | PASS |
| Retry + DLQ | 毒消息尝试 3 次，写入并从独立 DLQ Topic 回读 | PASS |

## 结论与边界

Java Producer → Kafka → Python Consumer 的跨语言事件链路成立，至少一次投递下使用 `eventId` 去重并在失败 3 次后写入 DLQ 的策略可行。

本 Spike 是单 Broker、单 Partition、无 TLS 的本地测试；Consumer 的内存去重只用于证明语义，不能作为生产实现。Phase 2 需要持久化幂等键、Schema Registry/契约、重平衡测试、监控告警和多 Broker 故障恢复。

## 决策

**通过**：接受 ADR-0002 的异步通信方向，但生产保障项必须进入模块 SDD。
