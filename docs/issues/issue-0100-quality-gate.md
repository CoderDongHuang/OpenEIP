# v0.9 完整模块质量门禁

> 2026-10-26：未通过。历史 GET 的局部门禁不等于完整模块或发布门禁。
> 六项均须绑定最终候选提交；不允许以文档提案替代实测。

| OEP 门禁 | 必须交付 | 当前证据与结论 |
|---|---|---|
| 覆盖率 | 完整新增运行代码单元测试通过，模块 ≥80%，关键并发/故障分支覆盖 | GET 历史 25/25、86%；新增共享状态/探针/负载/恢复路径未完成，Pending |
| 静态分析 | Java/Python/Frontend 及新增脚本、部署契约检查通过 | 历史 CI 通过；完整候选尚不存在，Pending |
| Benchmark | PERF-001～005、008 三轮业务对比、扩缩容、24h 长稳、数据故障/恢复合格 | 仅 fixture P99 34.292ms，不能推断业务/容量/HA，Pending |
| 安全扫描与审查 | 无 HIGH/CRITICAL；凭据、租户、管理面、Redis/租约故障安全及独立审查 | 历史扫描不是新功能证据；[安全审查](issue-0100-security-review.md)待决定，Pending |
| API 文档 | 运维 OpenAPI、结果 Schema、CLI、状态/部署/测试契约与实现一致 | 设计契约已补提案，运行实现未交付，Pending |
| 兼容性 | 既有 REST/SSE/SDK/SPI/事件、数据库正反迁移和旧数据回归通过 | 不能再对完整模块声明 DB/API 全部 N/A，Pending |

## CI 和发布判定

旧 [main run 34688340556](https://github.com/CoderDongHuang/OpenEIP/actions/runs/34688340556)
六项 job 成功，但整栈条件未命中。未来须显式执行候选整栈、真实 Redis/MySQL 竞争、
多副本、故障与恢复验证，附日志/报告和 SHA。mock 与 loopback 不能代替这些结果。

[测试矩阵](../13-testing/v0.9-performance-test-plan.md)任一必需项缺失即未通过。
六项全部通过后才能提交完整实现 PR；架构审查必须更早通过。
本轮文档 PR 仅用于纠偏设计审查，不表示实现 PR 门禁或发布条件满足。
