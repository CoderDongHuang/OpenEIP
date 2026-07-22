# 13-testing（测试规范）

> 性能测试、安全测试、LLM 评估、混沌测试、Benchmark。
> 在 MVP 开发阶段逐步建立。

## Foundation 状态

- [x] Foundation 单元测试与 80% 覆盖率门禁
- [x] Java/Python/Frontend/Website 构建门禁
- [x] 仓库安全与错误配置扫描
- [ ] 集成测试规范
- [ ] 契约测试规范
- [ ] 性能测试方案（JMeter / k6）
- [ ] 安全测试方案（SAST / DAST）
- [ ] LLM 评估方案（RAGAS / 自定义评估）
- [ ] 混沌测试方案
- [ ] Benchmark 方案与基线

未勾选项目随业务模块和 Phase 1.5 Spike 建立，不伪造无业务代码阶段的性能基线。

## 模块计划

- [Auth Test and Benchmark Plan](auth-test-plan.md)
- [File Upload Test and Benchmark Plan](file-upload-test-plan.md)

## Auth 基线

- [x] 31 个 Unit / H2 Integration / MySQL Contract / Migration Rollback 测试
- [x] 指令覆盖率 96.33%
- [x] BCrypt 12 登录 P99 337.51ms（阈值 500ms）
- [x] Trivy 仓库与 75 个 Boot JAR 运行时依赖扫描
- [Auth Benchmark Evidence](results/auth-benchmark.json)

## File Upload 基线

- [x] 29 个 Unit / H2 Integration / MySQL / API / Event / Rollback 测试
- [x] 指令覆盖率 94.84%
- [x] 1 MiB 本地对象存储 P99 5.60ms（阈值 250ms）
- [x] Trivy 仓库与 75 个聚合 Boot JAR 运行时依赖扫描
- [File Upload Benchmark Evidence](results/file-upload-benchmark.json)
