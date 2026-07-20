# Plugins

插件目录——所有 Connector、Agent、Workflow Node 插件开发在此进行。

## 设计原则

- Plugin First：所有扩展通过 SPI/SDK 实现
- 独立编译、独立发布
- Marketplace 生态的基础

## 规划目录（尚未实现）

```
plugins/
├── connectors/      ← Connector 插件
├── agents/          ← Agent 插件
├── workflow-nodes/  ← Workflow Node 插件
└── themes/          ← 主题插件
```

插件运行时不属于 Foundation 当前能力，SPI 在 RFC 和 Spike 通过前保持草案状态。
