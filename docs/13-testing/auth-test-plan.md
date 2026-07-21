# Auth Test and Benchmark Plan

> Issue: [#42](https://github.com/CoderDongHuang/OpenEIP/issues/42) | 状态: Verified

## 测试层

| 层 | 环境 | 覆盖 |
|---|---|---|
| Unit | JUnit/Mockito | JWT、service、密码、限流、异常 |
| API Integration | H2 + MockMvc | 注册、登录、刷新、当前用户、401/403/429 |
| MySQL Contract | Testcontainers MySQL | Flyway、FK、RBAC、refresh replay、OpenAPI paths |
| Static | Checkstyle/Spotless/SpotBugs | 主代码和测试代码 |
| Security | Trivy source + unpacked Boot JAR | Secret、misconfig、运行依赖 CVE |

## Benchmark

- 环境: Java 21、MySQL Testcontainers、BCrypt strength=12。
- 准备: 固定测试用户，5 次 warmup。
- 测量: 30 次串行登录，记录每次端到端 service latency。
- 指标: P50/P95/P99、总耗时、通过状态。
- 验收: P99 < 500ms。
- 输出: `docs/13-testing/results/auth-benchmark.json`。

该基线只代表单机容器环境，不代表公网、多实例或生产数据库容量。

## 验证结果（2026-07-22）

- 31 个测试通过，JaCoCo 指令覆盖率 96.33%。
- MySQL 8.4.4 空库 migration、5 个外键、唯一约束、完整 Auth Contract、refresh replay 和 rollback 通过。
- 登录基准: P50 242.65ms、P95 319.58ms、P99 337.51ms，低于 500ms 门禁。
- Checkstyle、Spotless、SpotBugs 与 Trivy 仓库/运行时依赖扫描通过。
- 机器可读结果: [`results/auth-benchmark.json`](results/auth-benchmark.json)。
