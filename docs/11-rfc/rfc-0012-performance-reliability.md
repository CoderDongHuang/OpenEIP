# RFC-0012：完整 Performance 与可靠性工程

## Status

Proposed，2026-10-26。尚未公开讨论、投票或接受；本地拟稿日期不作为讨论起点。
这是对遗漏范围的设计补齐，不是对既有版本的发布批准。

## Abstract

按 OEP / PRD / SAD 交付压测、调优、高可用、扩容、稳定性五项能力，
将 RFC-0011 的 GET 工具保留为其中一个测量组件。

## Motivation

此前工具仅验证确定性 loopback HTTP；无业务混合负载、应用/存储 HA、扩缩容或恢复证据。
把这些能力列为永久不在范围内与 v0.9 列车不一致。详细差距见
[纠偏记录](../issues/issue-0100-scope-correction.md)。

## Design

规范设计见 [Performance SDD](../04-sdd/performance-module.md)。完整交付包括：

- 认证业务负载、领域基准与 SSE 测量，固定数据集、资源规格和参考版本，失败不从指标中消失。
- 有限连接池/并发/队列，复用客户端，按测量定位锁竞争；保留取消与幂等语义。
- Java/Python 三副本、Frontend 两副本，稳定密钥、RWX 共享文件、依赖 readiness、滚动摘流。
- Redis 原子共享限流/计算去重，MySQL 事务保护调度和业务状态；无 Python 直写 Java 业务表。
- HPA、PDB、拓扑分散、资源预算；MySQL/Redis/Kafka/Milvus/ES/MinIO 各自有 HA 部署及故障证据。
- 长稳、单副本/节点/依赖故障、备份恢复、跨检索存储修复；Prometheus/Grafana/OTel 与告警。
- 候选引用、镜像标签及 Release 的一致性验证，最终提交整栈与多节点验收。

Kubernetes 在本版本承担 SAD 已定义的 HA/HPA 最小部署；v1.0 的 SSO/LDAP、
完整 Helm 分发和 LTS 支持责任仍属后续列车。对象存储产品化不在本轮改造：
先使用经故障测试的共享文件系统，MinIO 仍必须验证其在检索基础设施中的 HA。
外部托管数据服务可作为一种拓扑，但不能用“外部负责”省略切换/恢复验收。

## Alternatives Considered

| 方案 | 优点 | 不采用的原因 |
|---|---|---|
| 只发布 GET 工具并把其余能力排除 | 变更小 | 不能完成用户要求的整个 v0.9 模块 |
| 只增加副本数/HPA YAML | 配置简单 | 临时密钥、本地状态、文件卷及调度竞争未解决 |
| 同时引入新业务数据库与全新编排平台 | 可重新设计所有状态 | 偏离现有 Java/MySQL、Redis、Kubernetes 架构，扩大兼容风险 |
| 沿既有架构补齐共享状态、部署与验证 | 保持业务接口兼容，可逐项验收 | 选用；需要实际多节点资源与外部审查 |

## Impact

- API：新增内部运维探针/指标契约，既有业务 REST/SSE 及 v1 GET 结果兼容；详见[契约](../06-api/performance-operations.md)。
- SDK / Plugin SPI：签名、生命周期和安装格式不变，不增加新业务扩展点。
- Database：Java 管理调度租约表/索引及回滚；Redis 存放限流和非权威计算协调，见[状态设计](../05-database/performance-state.md)。
- Security：共享凭据、管理面网络隔离、租户级 key、Secret 脱敏、分布式限流故障关闭、安全扫描与独立审查。
- Operations：增加 Kubernetes、监控配置、备份恢复及容量报告；基础设施成本与测试时长上升。
- Events：沿用既有 eventId/outbox/inbox，不改变 Kafka 版本化 Schema；至少一次语义保留，不宣称任意外部副作用 exactly-once。

## Migration Plan

先备份及添加租约表/索引，部署兼容版本但保持单副本，再切换共享 Secret、RWX 文件、
Redis 协调与统一配置，通过双实例竞争和读写检查后启用多副本/HPA。
关闭 HPA 并缩回单副本是应用回退步骤；关闭新租约使用者后才允许回滚新增表，
保留业务数据、对象和审计。没有旧数据转换证明不得切换存储。

## Discussion and Decision

完整设计的公开 Discussion 链接、开始时间、至少一周讨论期、维护者投票与理由均待记录。
Discussion #128 只覆盖 RFC-0011；其时间不能自动计入本 RFC。所需赞成数为
`ceil(2 × 当期维护者人数 / 3)`。不把本对话中的继续工作授权登记为公开 RFC 投票。
接受 RFC、ADR、架构审批后才能开始新增实现。重大设计变更需重新审查。

## References

- [OEP](../../OEP.md)
- [PRD](../02-prd/zh-CN.md)
- [SAD](../03-sad/zh-CN.md)
- [ADR-0021](../12-adr/adr-0021-performance-shared-state-ha.md)
- [测试计划](../13-testing/v0.9-performance-test-plan.md)
