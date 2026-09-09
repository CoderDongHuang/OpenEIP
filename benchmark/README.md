# Benchmark

性能基准测试必须公开、可复现，并且明确测试边界。当前 v0.9 切片提供
标准库实现的 HTTP GET 基线工具；它只测量显式指定端点的控制面延迟和
传输结果，不代表生产容量、HA、扩容或真实模型质量。

## 运行

目标 URL 是必填项，私有/回环地址还必须显式加
`--allow-private-network`，避免误压生产环境：

```bash
./benchmark/run-benchmark.sh http://127.0.0.1:8000/health benchmark-result.json \
  --allow-private-network --requests 200 --concurrency 4 --warmups 10 \
  --timeout 2 --max-p99-ms 100
```

也可以直接运行 `python3 benchmark/run_benchmark.py --help` 查看全部参数。

## 结果契约

输出 JSON 包含 `schemaVersion`、请求配置、脱敏目标、P50/P95/P99/min/max、
吞吐量、状态码、错误分类和 `result`。错误、非 2xx 或超出
`--max-p99-ms` 时结果为 `FAIL`。响应体只读取到配置上限，且不会写入结果。

## 既有基准

文档解析、RAG、Embedding、Agent 等领域基准仍由各自 Python benchmark 测试
维护；本工具用于统一 HTTP 边界回归。v0.9 证据见
[`docs/13-testing/results/v0.9-performance-baseline.json`](../docs/13-testing/results/v0.9-performance-baseline.json)。
