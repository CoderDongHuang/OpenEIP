# Spike-005 验证报告：LLM Streaming E2E

## 概述

| 项目 | 值 |
|---|---|
| 执行时间 | 2026-07-21T10:22:31Z |
| 环境 | Chromium 136；Nginx 1.27.4；Python 3.12；OpenAI Client 1.68.2 |
| 原始证据 | [`results/result.json`](results/result.json) |
| 状态 | 通过 |

## 实测结果

| 验证项 | 验收标准 | 实际结果 | 判定 |
|---|---|---|---|
| Normal Stream | 首 Token < 500ms；总耗时 < 3s | 首 Token 179.3ms；总耗时 396.4ms；输出完整 | PASS |
| Upstream Error | 浏览器可见错误 | 后端计数 1，页面进入 `error` 状态 | PASS |
| Cancellation | 后端观察客户端取消 | `client_cancellations=1` | PASS |
| Reconnect | 取消后再次完成 | 第二次返回 `OpenEIP streaming works.` | PASS |

## 结论与边界

Chromium → Nginx Gateway → FastAPI → OpenAI-compatible 上游 → SSE → Chromium 的链路可行。Nginx 必须关闭代理缓冲，FastAPI 必须返回 `X-Accel-Buffering: no`，前端需要显式处理 token、done、error 和取消状态。

本地上游仅验证 OpenAI-compatible Streaming 协议和传输行为，不代表真实模型的首 Token、总延迟或内容质量。Phase 2 需要增加认证、配额、真实供应商适配、重试幂等和多实例断流恢复。

## 决策

**通过**：接受 SSE 作为 Gateway 到 Browser 的流式输出方式。
