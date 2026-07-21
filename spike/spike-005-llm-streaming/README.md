# Spike-005：LLM Streaming E2E

## 目标

通过真实 Chromium 验证 Frontend → Nginx Gateway → FastAPI → OpenAI-compatible Streaming 上游 → SSE → Frontend 的端到端链路。

## 验收标准

| 验证项 | 标准 |
|---|---|
| Normal Stream | 首 Token < 500ms，总耗时 < 3s，页面输出完整 |
| Upstream Error | 上游 503 转换为浏览器可见 `error` 事件 |
| Cancellation | 浏览器 `AbortController` 取消后，后端观察到取消 |
| Reconnect | 取消后再次发送请求并完整收到响应 |

## 运行

```powershell
./run.ps1
```

Compose 使用本地确定性 OpenAI-compatible fixture，不需要外部 API Key。成功证据写入 [`results/result.json`](results/result.json)。本 Spike 结论只覆盖流式传输，不代表真实模型的内容质量或公网延迟。

详细实测结论见 [report.md](report.md)。
