# Changelog

本文件记录 OpenEIP 所有值得注意的变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [Semantic Versioning](https://semver.org/)。

## [Unreleased]

### Added
- Document Parsing MVP 子集（Issue #46）
  - 严格 UTF-8 与 `ocr-result.v1` 输入、NFC/换行规范化和可配置重叠分块
  - 字符区间、页码、顺序、SHA-256、确定性 Chunk ID 与解析幂等键
  - `document-parsed-result` 和 `document.lifecycle.parsed` v1 Schema（事件不含原文）
  - 32 个解析测试、全量 Python 97.52% 指令覆盖率和 1 MiB 吞吐基准
- OCR Python 执行模块（Issue #45）
  - 版本化内部 API、租户/用户身份上下文、固定时间服务凭据校验和标准结果 Schema
  - PNG/JPEG 容器验证、5 MiB/尺寸/像素/帧限制与解压炸弹防护
  - 可替换 Provider 端口和范围明确的确定性 5x7 栅格识别器
  - 28 个测试、96.21% 指令覆盖率和完整 OCR 流水线基准
  - Python 3.12.13/Debian 13.6 固定镜像及 CI 运行时扫描
- File Upload 文档控制平面（Issue #44）
  - 认证用户的流式上传、列表、详情、下载和幂等删除
  - owner/admin 访问控制、10 MiB 限制、MIME/后缀校验、SHA-256 和安全对象键
  - MySQL 元数据、可逆 Migration、对象存储端口和失败补偿
  - `document.file.uploaded` v1 事件 Schema、OpenAPI、MySQL/API/Event Contract
  - 29 个测试、94.84% 指令覆盖率和 1 MiB 上传基准
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
- CI 将 OCR Benchmark 与覆盖率探针隔离，并增加 Python 运行时镜像 HIGH/CRITICAL 扫描
- Java 运行时使用 `platform-app` 聚合 Auth 与 Document 模块；共享响应信封和请求 ID 移入 `platform-common`
- CI 对所有 PR base 运行，安全扫描改为展开聚合 Java 运行时
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
