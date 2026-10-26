# v0.9 Performance 共享状态设计

> Proposed，2026-10-26。字段/算法设计先于 Migration；尚无已执行的 v0.9 Migration。

## MySQL：调度权威与租约

Java 各业务模块保持原表所有权。新增 `performance_scheduler_leases`，仅供已审计的后台工作包
claim/续租/提交使用，不提供业务 CRUD API，不允许 Python 直写。

| 字段 | 类型/约束 | 含义 |
|---|---|---|
| id | VARCHAR(36)，主键 | UUID |
| tenant_id | VARCHAR(36)，NOT NULL | 原任务租户；系统任务使用文档化固定 system tenant |
| task_type | VARCHAR(32)，NOT NULL | 白名单 `workflow_cron/workflow_execution/agent_run/outbox` |
| task_id | VARCHAR(128)，NOT NULL | 来源业务任务的稳定标识 |
| owner_id | VARCHAR(128)，NOT NULL | 本次进程启动生成的唯一 owner，不能仅用可复用 pod 名 |
| fencing_token | BIGINT，NOT NULL | 从 1 起单调增加；溢出拒绝而非回绕 |
| lease_until | DATETIME(6)，NOT NULL | 使用数据库 UTC 时钟比较，避免节点时钟偏移 |
| created_at / updated_at | DATETIME(6)，NOT NULL | 数据库 UTC 时间 |

索引：`uk_performance_scheduler_leases_task(tenant_id, task_type, task_id)`；
`idx_performance_scheduler_leases_expiry(lease_until)`。业务任务唯一键继续生效，
cron 以 `(tenant, triggerId, scheduledInstant)` 去重，outbox 保留原 eventId。

初始 lease 30s、每 10s 续租；值必须满足 `renewInterval < leaseDuration / 2`。
claim 在短事务内锁定唯一 key，使用 `SELECT ... FOR UPDATE`：无行则插入，唯一冲突后重读；
租约未过期且 owner 不同则跳过；已过期则 owner 替换、token 加一。任务终态不复用 key。
续租要求 owner/token 一致且尚未过期；失败立即停止继续发起副作用。
提交时再次在同一业务事务检查 owner/token/expiry，并执行条件状态更新及 outbox 写入。
IO 在事务之外；不能持行锁等待远程模型。release 只缩短到当前时间且保留 token 行；
不得删除活跃任务租约，否则 token 重置会失去 fencing 效果。
清理只处理超过任务/重放保留期的终态任务（最低 7 天），与业务任务清理顺序一致。

租约只保护已接入路径，不会自动修复所有调用。必须逐条验证人工触发、cron、重试、审批、
Kafka 重放、Agent worker 和多实例 outbox；不支持外部幂等的副作用出现 unknown outcome 时等待核对。

## Redis：共享限流与计算协调

连接使用 TLS/ACL，Secret 注入；key 不保存凭据/原文。应用不得依赖 Redis 缓存作为唯一业务记录。

| 用途 | key 结构 | TTL/上限 | 原子语义 |
|---|---|---|---|
| Auth/Webhook fixed window | `openeip:v1:limit:{scope}:<kind>:<hmac-key>:<window>` | 当前窗结束 + 1 窗；每 scope 有界 key 配额 | Lua 内计数、设过期、返回 allowed/remaining/retryAfter；集群多 key 使用相同 hash tag |
| Embedding claim | `openeip:v1:embedding:{tenant-job-digest}:claim` | 30s，每 10s 按 owner 续租 | SET NX + compare-owner renew/delete；不同 fingerprint 返回冲突 |
| Embedding result | `openeip:v1:embedding:{tenant-job-digest}:result` | 24h；值上限 1MiB，条目有界 | 校验 fingerprint、模型/版本/数据摘要后复用；不存原文，失败结果不缓存为成功 |

Auth 未认证请求没有可信 tenantId，scope 使用固定认证域和受信代理解析后的来源，
不能信任用户提供的租户 Header。已认证业务使用验证后的 tenant，HMAC secret 跨副本一致。
Webhook key 继承原 hookId 边界。上限、时间窗口和 HTTP 429 / Retry-After 保持原行为。
Redis 错误返回安全 503；禁止 fallback 到本地计数。断连/切换后至少完整窗口恢复屏障，
全新副本启动也先完成一个窗口屏障；需要保证的数据面故障关闭必须实测，不靠异步复制假定。

Embedding fingerprint 绑定 tenant/job、输入与 source hash、模型/版本、维度、分块及检索目标。
同 key 不同 fingerprint 返回既有 409；同 key 运行中短暂有界等待，超时返回可重试 503。
Redis claim 只是计算协调，不提供跨故障 fencing 权威；缓存失效可能重算，必须保持确定性
record key 的 upsert 幂等与版本检查。Redis 故障期间拒绝新增计算，不保存仅本地成功记录。
在目标索引不能防止旧版本覆盖前，不允许并发写入同一文档不同版本；由 Java 权威任务串行调度。

## 迁移与回滚

候选文件名 `V2.9.0__performance_scheduler_leases.sql`、对应
`U2.9.0__performance_scheduler_leases.sql`，实施前按仓库当前版本检查冲突。
只新增表/索引，先升级所有兼容读写者，保持单副本验证后才开启分布式调度。
回退先关闭 HPA、停止新 worker/调度、缩回单副本并确认无活跃租约，再移除新增表；
不删除已有业务/审计/对象数据。不支持旧进程与新进程同时独立调度同一任务的混合模式。

必须使用真实 MySQL 8.4/Redis 集成验证：正向迁移、旧数据保留、回滚、双 owner 竞争、
进程崩溃/续租失败、过期旧 owner 提交拒绝、token 单调、租户隔离、限流上限、Redis failover
及缓存丢失重算。结果须绑定候选 SHA；mock 的成功不能代替以上证据。
