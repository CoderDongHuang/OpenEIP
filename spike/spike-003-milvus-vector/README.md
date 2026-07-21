# Spike-003：Milvus Vector Search

## 目标

验证本地 OpenAI-compatible Embedding → Milvus 2.5.6 HNSW Collection → Insert → ANN Search 的完整链路，并分别记录 Embedding 与 Milvus 查询延迟。

## 验收标准

| 验证项 | 标准 |
|---|---|
| Collection | 创建 64 维 HNSW/COSINE 索引并加载成功 |
| Insert | 2000 条文档向量全部写入 |
| Search | 20 次预热后执行 200 次查询，Milvus Search P99 < 100ms |
| Correctness | 5 类确定性语义数据的 Top-1 准确率为 100% |

## 运行

```powershell
./run.ps1
```

Compose 启动 etcd、MinIO、Milvus、Embedding fixture 和 Runner。fixture 只用于离线、可重复的链路验证，不代表生产 Embedding 模型质量。成功证据写入 [`results/result.json`](results/result.json)。

详细实测结论见 [report.md](report.md)。
