# SDK

多语言 SDK——供插件开发者和第三方集成使用。

## 规划目录（尚未实现）

```
sdk/
├── java/       ← Java SDK（Maven Central）
├── python/     ← Python SDK（PyPI）
├── go/         ← Go SDK
└── javascript/ ← JavaScript/TypeScript SDK（npm）
```

## 设计原则

- API First：先生成 OpenAPI 规范，再生成 SDK
- 统一的错误处理
- 统一的认证方式
- 支持同步（REST）和异步（SSE/WebSocket）调用

SDK 不属于 Foundation 当前能力。首个 SDK 必须由稳定 OpenAPI 契约生成并通过 RFC。
