# ADR-0021：Performance 共享状态、HA 与可验证发布

## Status

Proposed；待 RFC-0012 决定及架构审查，不是已接受的架构。

## Date

2026-10-26

## Context

SAD 要求服务无状态、多副本/HPA、Redis 协调与 MySQL 事务。当前单节点实现存在
进程限流/去重、临时密钥、本地文件与静态健康检查；多副本配置本身不能消除这些问题。
ADR-0020 仅适用于 GET 工具，不能作为完整模块的设计接受记录。

## Decision

提议在既有架构中完成以下改造：

1. Java/MySQL 保持业务权威；数据库租约、行级锁/唯一约束保护调度与状态迁移，租约含单调 fencing token。
2. Redis 用有界、租户隔离的 Lua 原子操作共享 Auth/Webhook 限流及 Embedding 计算协调。
   Redis 不作为业务提交日志；缓存丢失后的安全性由确定性 upsert 与 Java 权威状态保证。
3. 业务凭据统一从 Secret 引用；多副本禁止临时 JWT、本地默认 Secret、进程去重后端和节点本地文件。
   文件先用支持一致 POSIX 语义的 HA RWX 存储，保持 ObjectStorage 端口，不在本轮强行更换产品接口。
4. Kubernetes 提供应用副本、readiness、PDB、拓扑分散、HPA 与滚动发布；数据层独立拓扑、备份及恢复验收。
5. 保留 GET 结果 v1，增加独立业务/可靠性证据；监控统一使用 SAD 既定 Prometheus/Grafana/OTel。
6. 不对已输出 Token 的流式请求、非幂等写入或不支持去重的外部工具自动重放。

详细参数、租约算法、状态边界、故障语义及实施工作包见
[SDD](../04-sdd/performance-module.md)、[状态设计](../05-database/performance-state.md)和
[部署方案](../10-deployment/v0.9-performance-ha.md)。

## Consequences

### Positive

- 延续 Java/Python、MySQL、Redis 既有边界，业务 SDK/SPI 无需重编译。
- 将多副本安全、扩缩容和数据恢复转成独立可验收项。
- 全部性能声明绑定候选提交、负载、资源和原始证据，便于比较与回滚。

### Negative

- 多节点、数据服务故障与 24 小时长稳测试需要额外资源。
- Redis 故障时安全限流拒绝请求，牺牲部分可用性；不能静默切回进程计数。
- RWX 存储的延迟和故障域需要单独测试，不能把本地卷当作替代。

### Risks

- Redis failover 可能丢失计数/缓存：恢复屏障与整窗拒绝避免超额放行；计算协调不能承诺跨故障精确一次计费。
- 租约过期旧 Worker 仍可运行：提交必须校验 fencing token；无幂等支持的外部副作用不得自动接管重放。
- 数据层副本并不自动消除丢失：分别验证 RPO/RTO，并对 ES/Milvus 不一致执行有界修复。
- 文档可能被误读为已实现：所有新增设计保持 Proposed，验收表缺证据即不通过。

## Alternatives Considered

| 方案 | 优点 | 缺点 | 决定 |
|---|---|---|---|
| 进程内状态 + 粘性会话 | 成本低 | 扩缩容/重启仍丢失状态，安全限额可被跨副本绕过 | 不采用 |
| 所有缓存和限流都写 MySQL | 事务强 | 扩大热点和数据库负载，偏离 SAD Redis 职责 | 限业务权威/调度使用 MySQL |
| Redis 保存全部业务权威 | 延迟低 | 破坏数据所有权，异步复制丢失风险 | 不采用 |
| 独立事务权威 + Redis 协调 + 真实 HA 验证 | 契合基线 | 运维/测试成本较高 | 提议采用 |

## Acceptance

架构负责人、决策时间和 GitHub 记录链接待填写。不得以本地自审或实现 PR 既有 Approval 代替本决策接受。
