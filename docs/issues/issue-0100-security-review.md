# v0.9 完整模块安全审查

> 2026-10-26：威胁模型提案；新增控制尚未实现/实测，独立安全审查未通过。

| 威胁 | 设计控制 | 必须验证的证据 |
|---|---|---|
| 负载工具 SSRF/DNS rebinding/重定向/过载 | 保留 GET pin/私网显式授权/有界读取；业务 CLI allowlist、TLS、并发/时长限制 | URL/DNS/超时/取消/大小边界与真实目标约束 |
| 凭据、文档和租户泄露 | Secret 注入，证据仅摘要，metrics/trace 不记录原文/token | 日志/JSON/trace 脱敏与跨租户拒绝测试 |
| 多副本 JWT/文件不一致 | 稳定共享密钥、受限 HA RWX、轮换/访问审计 | 跨副本登录、上传下载、权限、滚动升级与密钥轮换 |
| Redis 故障绕过限流 | 原子计数、可信 scope、故障关闭和恢复屏障 | 双副本总上限、伪造 Header、failover、重启/冷启动 |
| 旧 worker 重复执行或越权提交 | DB 租约/owner/token 条件提交，业务幂等，未知外部结果待核对 | 真实 DB 双 owner、崩溃/续租失败/过期提交、事件重放 |
| Embedding 去重冲突/缓存污染 | tenant+fingerprint、owner 校验、TTL/大小限制、确定性 upsert | 两实例相同/冲突请求、缓存丢失/Redis 故障、旧版本写入拒绝 |
| 管理面暴露或指标基数攻击 | 独立管理网络/端口、NetworkPolicy、公网拒绝、route template 标签 | 公网负向测试、管理网抓取、恶意路径/label 基数边界 |
| 故障演练损坏真实数据 | 隔离环境/测试租户/显式目标、备份与恢复点、清理校验 | 演练目标核对、备份恢复、权限及跨存储对账 |
| 依赖/镜像/Secret 漏洞 | 依赖审计、Trivy、镜像 digest/attestation、最小权限 | 最终候选无 HIGH/CRITICAL，日志与扫描产物链接 |

历史 GET 的 25 个用例与 [main CI 34688340556](https://github.com/CoderDongHuang/OpenEIP/actions/runs/34688340556)
的自动扫描仅覆盖旧提交；不代表以上新增控制通过。新增实现须按
[测试矩阵](../13-testing/v0.9-performance-test-plan.md)实测并取得独立安全决定。

独立审查者 / 时间 / 候选 SHA / 结论 / 证据链接：**待记录**。
发现不可接受的租户隔离、重复副作用、数据丢失或管理面泄露必须阻止发布。
