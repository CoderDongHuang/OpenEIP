# Spike-003 验证报告：Milvus Vector Search

## 概述

| 项目 | 值 |
|---|---|
| 执行时间 | 2026-07-21T10:20:28Z |
| 环境 | Milvus 2.5.6 Standalone；HNSW/COSINE；Python 3.12 |
| 原始证据 | [`results/result.json`](results/result.json) |
| 状态 | 通过 |

## 实测结果

| 验证项 | 实际结果 | 判定 |
|---|---|---|
| Embedding | 本地 OpenAI-compatible fixture；64 维；2000 文档 932.16ms | PASS |
| Insert | 2000/2000 向量写入；67.71ms | PASS |
| Search latency | 200 queries；P50 3.224ms；P95 4.018ms；P99 4.976ms | PASS（P99 < 100ms） |
| Query embedding | P50 2.003ms；P95 2.608ms；P99 3.491ms | 记录 |
| Correctness | 200/200 查询 Top-1 类别正确 | PASS |

## 结论与边界

Milvus Standalone 的 Embedding → Insert → HNSW Search 链路可行，ADR-0003 可接受 Milvus 作为 Knowledge 阶段候选向量存储，但它不进入 Foundation 默认启动面。

确定性 Embedding fixture 用于消除外部 API 波动并验证协议、数据与检索链路，不代表生产模型的语义质量。2000 条数据也不能外推到生产规模；Phase 3 必须使用目标模型和真实语料重新做召回率、并发、资源、备份恢复及扩容基准。

## 决策

**有条件通过**：技术链路成立；生产选型以 Phase 3 数据集和容量验证为准。
