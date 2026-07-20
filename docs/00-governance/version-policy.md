# Version Policy（版本规范）

> 本文档定义 OpenEIP 的语义化版本细则、Breaking Change 判定标准和版本兼容性承诺。

---

## Semantic Versioning 细则

遵循 [SemVer 2.0.0](https://semver.org/)：

```
MAJOR.MINOR.PATCH

示例：1.2.3
      │ │ │
      │ │ └─ PATCH：向后兼容的 Bug 修复
      │ └─── MINOR：向后兼容的功能新增
      └───── MAJOR：不兼容的 API 变更
```

## Breaking Change 判定标准

以下变更属于 **Breaking Change**，必须递增 MAJOR 版本：

> `v1.0.0` 之前属于公开 API 稳定化阶段。`0.x` 版本发生 Breaking Change 时递增 MINOR，并在 Release Notes 和 Migration Guide 中明确说明；进入 `v1.0.0` 后才执行下述 MAJOR 规则。

### API

- 删除已有的 REST API 端点
- 修改已有端点的请求/响应字段名或类型
- 修改已有的错误码含义
- 修改认证方式
- 删除已有的 SSE 事件类型

### SDK

- 删除公开的类/方法/函数
- 修改公开方法的签名（参数类型、返回值类型）
- 修改公开方法的行为语义
- 修改 SDK 的依赖版本（导致下游冲突）

### Plugin SPI

- 删除已有的 SPI 接口
- 修改已有 SPI 接口的方法签名
- 修改已有 SPI 的契约语义

### Database

- 删除已有表的字段
- 修改已有字段的类型
- 删除已有的表

### 不属于 Breaking Change

- 新增 API 端点
- 新增可选字段
- 新增错误码
- 新增 SDK 类/方法
- 新增 SPI 接口
- 新增数据库表和字段
- 标记废弃（@Deprecated）但不删除

## 版本兼容性承诺

### 同一 MAJOR 版本内

- API：完全向后兼容
- SDK：签名和行为不变
- Plugin SPI：已有插件无需修改即可运行
- Database Migration：正向执行 + 可回滚

上述完整兼容承诺从 `v1.0.0` 开始生效。`0.x` 版本在同一 MINOR 的 PATCH 发布中保持兼容。

### 跨 MAJOR 版本

- 提供 Migration Guide
- 废弃的 API/SDK/SPI 在一个 MAJOR 版本后移除
- 数据库 Migration 提供兼容性检查脚本

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1.0 | 2026-07-20 | 初始版本，定义 SemVer 细则 |
