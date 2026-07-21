# Changelog

本文件记录 OpenEIP 所有值得注意的变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [Semantic Versioning](https://semver.org/)。

## [Unreleased]

### Added
- Auth 认证授权模块（Issue #42）
  - 用户注册/登录、RS256 access/refresh token 和数据库单次轮换
  - 数据库实时 RBAC、角色查询/创建/分配与禁用账户拦截
  - 统一响应信封、请求 ID、JSON 401/403 和认证端点限流
  - Flyway Schema、幂等引用数据初始化和可验证 rollback
  - Unit、H2 API、MySQL Contract、OpenAPI Contract 和登录 Benchmark
- Phase 1.5 Technical Validation：5 个可复现 Spike、固定依赖、Compose 编排和机器可读证据
- Java 21 ↔ Python 3.12 gRPC unary/streaming/error 验证
- Kafka 3.9.1 跨语言事件、幂等、重试和真实 DLQ 验证
- Milvus 2.5.6 Embedding/Insert/HNSW Search 正确性与性能验证
- 官方 MCP SDK 初始化、工具发现、调用和错误验证
- Chromium 经 Nginx/FastAPI/OpenAI-compatible 上游的 SSE、错误、取消和重连验证

### Changed
- 接受 ADR-0002 和 ADR-0004；ADR-0003 以生产化前置条件接受
- SAD 与 SDD 基线升级到 1.1，纳入 Phase 1.5 实测边界

## [0.1.0-alpha] - 2026-07-21

### Added
- 项目初始化：Phase -1 Project Governance 完成
- 10 条 Engineering Principles
- 完整治理文档体系（Maintainers / Committers / Release Policy / Version Policy / Branch Strategy / Coding Standard / RFC Process / ADR Process / Security Policy / Community）
- PRD、SAD、SDD Foundation 基线与首批 RFC/ADR
- 可构建的 Java、Python、Frontend 与 Docusaurus 脚手架
- Docker Compose Foundation 运行环境
- Repository Bootstrap
- Architecture Baseline
- CI 构建、测试、覆盖率、格式与安全扫描
