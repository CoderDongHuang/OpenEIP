# v0.9 完整设计架构审查清单

> Proposed / 待外部审查。2026-10-26。
> OEP Step 6 为编码前门禁；此前 GET 的代码 Approval 不能替代本决定。

基线：[PRD](../02-prd/zh-CN.md) PERF-001～009、
[SAD](../03-sad/zh-CN.md) 9.4、[SDD](../04-sdd/performance-module.md)、
[RFC-0012](../11-rfc/rfc-0012-performance-reliability.md)、
[ADR-0021](../12-adr/adr-0021-performance-shared-state-ha.md)。

| 审查项目 | 设计依据 | 决定 |
|---|---|---|
| 五项范围与可验收目标 | PRD 0.8、SDD WP0～WP4 | Pending |
| Java 事务权威、Python 不写业务表、无跨层直连 | SDD、ADR-0021 | Pending |
| 数据库租约、fencing、未知外部副作用、清理和迁移回滚 | [状态设计](../05-database/performance-state.md) | Pending |
| Redis 限流/计算协调、故障关闭、跨副本一致性与缓存丢失 | 状态设计与双实例竞争测试 | Pending |
| 稳定 JWT、共享文件、依赖就绪、摘流及旧 API 兼容 | [运维契约](../06-api/performance-operations.md) | Pending |
| 三副本、HPA/PDB/资源预算、数据层 HA 和故障域 | [部署设计](../10-deployment/v0.9-performance-ha.md) | Pending |
| 业务基准/连接池/队列/锁调优，不平均百分位 | SDD、[测试矩阵](../13-testing/v0.9-performance-test-plan.md) | Pending |
| 管理面隔离、指标基数、OTel 隐私/采样、告警 | 运维契约、部署设计 | Pending |
| SDK/SPI/事件/DB/REST/SSE 兼容与升级边界 | SDD 与验收矩阵 | Pending |
| 备份恢复、RTO/RPO、24h 长稳和回滚可操作性 | 部署设计、测试矩阵 | Pending |

审查者、时间、审查提交、批准或修改意见链接：**待记录**。
只有真实架构决定可将此门禁改为通过；自查、CI 绿色、维护者在聊天中要求继续，
均不自动构成公开的 RFC 投票或独立架构审查记录。

历史 GET 的 URL 校验、DNS pin、禁止重定向和有界响应设计保留；
此前以“工具无运行时影响”为整个模块范围的结论已撤回，不能沿用 DB/UI/API 全部 N/A。
