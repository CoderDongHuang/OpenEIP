# v0.9 运维与性能证据契约设计

> Proposed；尚未提供这些新增接口。设计契约已提供，须经审查后再实施运行代码。
> [GET CLI v1](performance-benchmark.md)及原 JSON Schema 保持现状。

机器可读设计：[运维 OpenAPI](performance-management.openapi.json)、
[完整性能证据 Schema](../../contracts/performance/performance-evidence.v1.schema.json)。
OpenAPI 的 management.invalid 是不可用占位地址；各路径对应所属运行时的私有管理监听。
Schema 用 kind 区分三类 payload；允许缺失测量以 null/空轮次表示 INCOMPLETE，
不能因为结构校验通过就输出 PASS。语义验证器须额外检查计数恒等、时间顺序、三轮唯一性、
完整 SHA/配置/环境对应关系和阈值；所有数值有上限，JSON 拒绝 NaN/Infinity。
reasons 和版本信息也必须脱敏。reliability 的 RPO 以时间秒计，另报丢失持久记录数；
soak 仅在长稳实验填写，其他实验为 null，不得用它替代故障注入/恢复证据。

## 运维 HTTP 面

| 运行时 | 路径 | 返回与鉴权边界 |
|---|---|---|
| Python | `GET /health` | 保留原 `status/version/service` 响应兼容，只表示进程存活，不代表依赖就绪 |
| Python | `GET /health/live` | 200 `{"status":"UP"}`，事件循环/进程存活；不探测外部依赖 |
| Python | `GET /health/ready` | 200 `{"status":"UP"}` 或 503 `{"status":"DOWN"}`，必要配置/检索/Redis 就绪；不泄露依赖地址和错误栈 |
| Java | `GET /actuator/health/liveness` | 保持 Actuator 语义，200/503；不把数据库暂时故障作为重启理由 |
| Java | `GET /actuator/health/readiness` | 200/503，数据库、共享状态、文件存储及调度所需依赖；不返回 Secret 或内部拓扑 |
| Python | `GET /metrics` | Prometheus text 格式，独立管理监听/网络，业务 Gateway 禁止代理 |
| Java | `GET /actuator/prometheus` | Prometheus text 格式，独立 management port 与网络，业务 Gateway 禁止代理 |

探针仅暴露状态；management/metrics 通过 NetworkPolicy 与管理网隔离，不公开到 Ingress。
平台公网路径对 `/ai/metrics`、readiness 详情及 Actuator 管理面返回 404/拒绝。
探测结果短缓存最多 2s，单次依赖超时最多 1s、总预算 2s；失败自动移出 Service。
startup 未完成不接业务流量，liveness 不级联外部依赖故障，SIGTERM 立即取消 ready。
新增路径先写入运维 OpenAPI；不改变现有业务 envelope、分页、认证和 SSE 事件。

指标只使用有界 service/route template/method/status/outcome labels；route 未匹配统一为
`unmatched`，不得用原始路径或参数。延迟 histogram 必须可计算 P50/P95/P99。
指标/trace 不保存 Header、token、用户原文和检索正文；OTel 采样与保留期由部署配置控制。

## 新业务负载 CLI（独立于 GET）

计划入口 `benchmark/run_workloads.py`；仅面向隔离测试环境，必须显式提供
`--base-url`、`--environment-id`、`--dataset`、`--scenario`、`--output`、`--commit`。
凭据取受保护环境变量；不接受 URL userinfo 或命令行密码。目标按已审查 allowlist 验证，
禁止重定向换目标；私网只在明确测试拓扑中使用。没有全局自动探测目标。
请求上限 100,000、并发 256、每请求超时 300s、运行时长 24h；长期负载分批采样有界落盘，
不在内存中累计 24 小时的全部响应。参数必须为有限正数，超过边界立即拒绝。

每场景在独立测试租户/账号内创建数据，返回语义/权限/内容摘要校验后才计成功；
预热不计入测量。并发安全的幂等 key 在重试时复用。取消、超时、非预期状态、
SSE 缺失 done/错误事件、清理失败计入失败。TLS 校验默认启用，不提供默认跳过校验模式。

## 独立结果类型（设计 Schema 已提供，WP1 实现验证器）

旧 `performance-benchmark-result.v1` 字段不增删改。新增结果各自有 `schemaVersion: "1.0"`
和类型标识，Schema 要求 unknown fields 拒绝、所有计数非负、数值有限、单位明确。
公共字段：`kind`、`commit`（完整 SHA）、`generatedAt`（UTC）、`environmentId`、
`datasetSha256`、`topologySha256`、运行时/依赖/模型版本、CPU/RAM 规格、配置摘要、
`result`（PASS/FAIL/INCOMPLETE）、`reasons`。不保存 Secret、原始文档、完整敏感目标 URL。

| kind | 必需内容 | 失败/不完整规则 |
|---|---|---|
| `business-workload` | 场景、轮次、并发、请求/成功/失败/取消数，状态分类，elapsed/throughput、P50/P95/P99、SSE 首 Token/总时延、正确性/清理状态 | 零样本、缺关键指标为 INCOMPLETE；错误/内容校验失败为 FAIL |
| `performance-comparison` | 参考/候选 SHA 与结果摘要、环境匹配字段、每场景三轮中位 P99/吞吐、差值、明确阈值 | 环境不一致或参考缺失为 INCOMPLETE；退化超阈值为 FAIL |
| `reliability-experiment` | 故障对象与时间窗、基线/故障/恢复分段、原始计数、成功率、RTO/RPO、重复/丢失、恢复后完整性/权限检查 | 无故障动作/恢复观测为 INCOMPLETE；超时/丢失/越权为 FAIL |

统计口径：延迟含全部完成请求，包括错误；超时/取消另列，不从成功率分母排除。
吞吐使用完整测量墙钟时间；不平均百分位，逐轮计算后取三轮中位做比较。
退出码：0 仅 PASS，1 FAIL/INCOMPLETE，2 参数/配置无效。
JSON 构建与实际请求计数须一致，Schema 校验成功不等于结果业务有效。

## 数据、事件、SDK 与 UI 兼容

新增租约表与 Redis 布局见[状态设计](../05-database/performance-state.md)，不再对完整模块宣称无 DB 影响。
无新增公共业务 API、Kafka Schema、SDK 方法或 Plugin SPI；内部端口按分层实现。
Grafana 为管理 UI，仪表盘 JSON、变量、数据源/告警配置纳入版本控制。
升级前后执行既有 OpenAPI、SSE、SDK/SPI、事件、Migration 与权限测试。
