# Quality Gate: Issue #42 Auth 模块

> 状态: Passed
> 最后更新: 2026-07-22

| Gate | 标准 | 当前证据 | 状态 |
|---|---|---|---|
| Coverage | Unit + Integration >= 80% | 31 个测试；完整门禁指令覆盖率 96.33%（1892 covered / 72 missed） | Passed |
| Static Analysis | Checkstyle / Spotless / SpotBugs 无阻断问题 | main/test 全部通过，未全局关闭规则 | Passed |
| Benchmark | 登录 P99 < 500ms | 5 warmups + 30 measurements；P50 242.65ms、P95 319.58ms、P99 337.51ms | Passed |
| Security | 源码、配置、Secret、Boot JAR 无 HIGH/CRITICAL | Trivy 0.72.0 仓库扫描 + 75 个运行时 JAR 扫描均为 0 | Passed |
| API Docs | OpenAPI 与实现 Contract Test 一致 | MySQL Contract 读取 `auth-v1.openapi.yaml` 并验证 7 个 operations 和响应信封 | Passed |
| Compatibility | API/DB/SDK/SPI 检查通过 | API 仅新增；原 platform API 测试通过；MySQL migration/FK/unique/rollback 通过；SDK/SPI 未改 | Passed |

## 可复现命令

```powershell
cd java/platform
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine' # 仅当前 Windows Docker Desktop
./gradlew.bat --no-daemon clean check build
./gradlew.bat --no-daemon :platform-auth:authBenchmark
```

基准证据: [`auth-benchmark.json`](../13-testing/results/auth-benchmark.json)。

完整仓库级 Java、Python、Frontend、Website、Spike 和 Compose 检查均已在本分支执行。npm 在线审计在本机网络超时，离线 lockfile 审计为 0 漏洞；远程 CI 仍需完成在线审计后才可合并。
