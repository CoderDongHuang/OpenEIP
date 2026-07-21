# Spike-004：MCP Runtime

## 目标

使用官方 Python MCP SDK 验证 MCP Server 与 Client 的标准 stdio 生命周期，不使用自定义 REST/JSON-RPC 模拟。

## 验收标准

| 验证项 | 标准 |
|---|---|
| Initialize | `ClientSession.initialize()` 返回协议与 Server 信息 |
| Discovery | `tools/list` 返回 3 个工具及 inputSchema |
| Invocation | 3 个工具通过 `tools/call` 返回预期结果 |
| Errors | 非法参数和未知工具均返回 `isError=true` |

## 运行

```powershell
./run.ps1
```

成功证据写入 [`results/result.json`](results/result.json)。本 Spike 验证协议和 SDK 互操作性；鉴权、租户隔离、审计和远程 Transport 留待模块 SDD。

详细实测结论见 [report.md](report.md)。
