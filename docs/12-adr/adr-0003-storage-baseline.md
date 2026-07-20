# ADR-0003：存储组件按能力验证引入

## Status

Proposed - pending storage Spikes

## Date

2026-07-20

## Context

目标架构涉及关系数据、缓存、全文检索、向量检索、对象存储和图数据。一次性引入全部组件会提高最小部署成本，并掩盖真实需求。

## Decision

Foundation 默认部署不包含持久化组件。MySQL、Redis、Elasticsearch、Milvus、MinIO、Neo4j 和 ClickHouse 必须由对应模块需求与 Spike 数据证明后加入；每个组件需要所有权、备份、恢复、升级和退出方案。

## Consequences

### Positive

- 默认部署保持轻量。
- 每个存储选择都有可追踪证据。

### Negative

- SAD 中的数据架构在验证前只是目标架构。

### Risks

- 模块可能在验证前绑定厂商 API；通过仓储接口和契约测试缓解。

## Alternatives Considered

| 方案 | 优点 | 缺点 | 为何不选 |
|---|---|---|---|
| 默认部署全部组件 | 接近目标图 | 资源和运维成本过高 | 不适合 Foundation |
| 只使用单一数据库 | 运维简单 | 检索和向量能力可能受限 | 作为 Spike 对照组 |
