# Changelog

本文件记录 OpenEIP 所有值得注意的变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [Semantic Versioning](https://semver.org/)。

## [Unreleased]

## [0.2.0-alpha] - 2026-07-22

### Added
- Auth/RBAC 控制面（Issue #42）
  - 用户注册、登录、RS256 access/refresh token 单次轮换和数据库实时权限
  - Flyway Schema、H2/MySQL/API/OpenAPI Contract、登录 Benchmark 与安全评审
- File Upload 控制面（Issue #44）
  - 认证上传、列表、详情、下载和幂等删除，以及 owner/admin 访问控制
  - 本地对象存储端口、MySQL 元数据、事件契约、失败补偿和 10 MiB 安全边界
- OCR Python 执行模块（Issue #45）
  - 版本化内部 API、受限 PNG/JPEG 输入、确定性栅格 Provider 和标准结果 Schema
  - 尺寸、像素、帧和解压炸弹防护，以及完整流水线 Benchmark
- Document Parsing MVP 子集（Issue #46）
  - 严格 UTF-8 与 `ocr-result.v1` 输入、NFC/换行规范化和可配置重叠分块
  - 字符区间、页码、顺序、SHA-256、确定性 Chunk ID 与解析幂等键
  - `document-parsed-result` 和 `document.lifecycle.parsed` v1 Schema（事件不含原文）
  - 32 个解析测试、全量 Python 97.52% 指令覆盖率和 1 MiB 吞吐基准
- Knowledge Base 控制面（Issue #47）
  - 知识库和文档关联生命周期、owner/editor/viewer 权限、MySQL 持久化和事件状态机
  - Kafka 监听契约、幂等状态转换、Rollback、Benchmark 和租户边界测试
- Embedding MVP（Issue #48）
  - 严格批处理 API、确定性 Embedding Provider、内存向量仓库和租户/知识库隔离
  - 作业幂等、Provider 输出验证、精确检索 fixture 和批处理 Benchmark
- Grounded RAG（Issue #49）
  - 查询 Embedding、向量检索、可信 Prompt 构建、引用校验和确定性答案 Provider
  - 1,000 记录检索 Benchmark、Prompt Injection 边界和失败封装
- Streaming Chat（Issue #50）
  - MySQL Chat 会话与消息、Java 到 Python SSE 网关、取消和引用事件
  - React 登录/Chat 工作区、首 Token/完成延迟和并发流 Benchmark
- Constrained Agent Runtime（Issue #51）
  - Agent SPI v1、显式 Tool Allowlist、步骤/超时限制、临时观察和安全 SSE 事件
  - Java 授权网关、`document.inspect`/`knowledge.search` 工具和循环终止 Benchmark
- Phase 1.5 Technical Validation：5 个可复现 Spike、固定依赖、Compose 编排和机器可读证据
  - Java/Python gRPC、Kafka、Milvus、官方 MCP SDK 和浏览器 SSE 技术验证

### Changed
- Java `platform-app` 聚合 Auth、Document、Knowledge、Chat 与 Agent 模块。
- Python AI Engine 聚合 OCR、Parsing、Embedding、RAG、Chat 与 Agent Runtime。
- CI 对六项必需检查执行覆盖率、静态分析、Benchmark、依赖审计和 Trivy 运行时扫描。
- PRD、SAD、SDD、RFC、ADR、OpenAPI、数据库和 SPI 文档同步到 v0.2 技术基线。

### Fixed
- Java Runtime 镜像预创建并授权 `/app/data/files`，确保非 root `openeip` 用户可写新建的
  `document-files` Volume；该问题由发布候选整栈冒烟发现。

### Known limitations
- 本版本是单节点 alpha；文件存储使用本地 Volume，向量数据使用进程内存。
- OCR、Embedding、RAG、Chat 和 Agent 默认使用确定性 Provider，不宣称生产模型质量。
- Kafka、Milvus、远程 MCP、真实模型、HA、备份恢复和多租户生产隔离属于后续版本。

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
