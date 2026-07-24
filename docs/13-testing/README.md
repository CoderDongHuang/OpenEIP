# 13-testing（测试规范）

> v0.2 MVP 使用模块级 Unit/Integration/Contract、可复现 Benchmark、依赖审计和运行时扫描；
> 发布候选还必须通过真实 Compose 边界的整栈冒烟。

## 强制门禁

- [x] Java/Python 指令覆盖率至少 80%
- [x] Java Checkstyle、SpotBugs、Spotless 与 Python Ruff、Mypy
- [x] API、数据库、事件、Migration 和 SPI Contract 测试
- [x] 九个模块的可复现 Benchmark 与机器可读结果
- [x] npm/pip 依赖审计和 Trivy 仓库/Java/Python Runtime 扫描
- [x] `v0.2.0-alpha` 发布分支整栈 Compose 冒烟
- [ ] 真实模型质量评估、DAST、混沌、多节点恢复和容量测试（后续版本）

确定性 Provider Benchmark 只验证协议、边界、生命周期和性能回归，不代表真实 OCR、Embedding、
LLM 或 Agent 质量。生产模型、Milvus 容量和多节点故障测试必须在进入对应发布范围前补充。

## 模块计划与证据

| 模块 | 测试计划 | Benchmark 证据 | 已验证基线 |
|---|---|---|---|
| Auth | [Plan](auth-test-plan.md) | [Result](results/auth-benchmark.json) | 31 tests，96.33%，登录 P99 337.51 ms |
| File Upload | [Plan](file-upload-test-plan.md) | [Result](results/file-upload-benchmark.json) | 29 tests，94.84%，1 MiB P99 5.60 ms |
| OCR | [Plan](ocr-test-plan.md) | [Result](results/ocr-benchmark.json) | 受限栅格流水线 P99 24.07 ms |
| Document Parsing | [Plan](document-parsing-test-plan.md) | [Result](results/document-parsing-benchmark.json) | 1 MiB/1,172 chunks P99 60.63 ms |
| Knowledge Base | [Plan](knowledge-base-test-plan.md) | [Result](results/knowledge-base-benchmark.json) | 92.85%，状态转换 P99 7.78 ms |
| Embedding | [Plan](embedding-test-plan.md) | [Result](results/embedding-benchmark.json) | 97.91%，32-text batch P99 1.359 ms |
| RAG | [Plan](rag-test-plan.md) | [Result](results/rag-benchmark.json) | 98.11%，1,000-record P99 6.449 ms |
| Chat | [Plan](chat-test-plan.md) | [Result](results/chat-benchmark.json) | Java 95.28%，首 Token P99 5.762 ms |
| Agent | [Plan](agent-test-plan.md) | [Result](results/agent-benchmark.json) | Python 93.62% / Java 94.18%，完成 P99 0.116 ms |

最终合并快照必须重新运行全量门禁；上表的模块开发数据不能替代发布候选结果。

## v0.3 Knowledge Candidate

- [Test plan](v0.3-knowledge-test-plan.md)
- [Retrieval benchmark](results/v0.3-retrieval-benchmark.json): 1,000 records, P99 8.662 ms,
  exact Top-1.
- Live Milvus 2.6 and Elasticsearch 8.19 scoped upsert/search/delete integration passed.
- Python: 170 passed at 92.54%; frontend: 27 passed at 90.64% statements; Java all-module JaCoCo passed.

## v0.2.0-alpha 发布候选结果

- Java `clean check build`、两个 Java Spike 编译和全部静态/覆盖率门禁通过。
- Python 161 个非 Benchmark 测试和 6 个 Benchmark 通过，总指令覆盖率 97.00%。
- Frontend 12 个测试、生产构建和依赖 HIGH 门禁通过；Website 类型检查与构建通过。
- Compose 构建的 Gateway、Java、Python、Frontend、MySQL 五个服务全部健康。
- `scripts/release_smoke.py` 通过认证、文件、OCR、解析、知识库、Embedding、RAG、Chat、Agent
  和资源清理的真实 HTTP/SSE 边界。
