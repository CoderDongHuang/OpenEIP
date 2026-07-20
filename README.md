# OpenEIP

> Open Enterprise Intelligence Platform

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-v0.1.0--alpha-orange.svg)](CHANGELOG.md)

OpenEIP 是一个面向企业 AI 场景的开源平台项目。当前 `v0.1.0-alpha` 是 Foundation 版本，交付工程治理、产品与架构基线，以及可构建的 Java、Python、Frontend 和文档站脚手架。

RAG、Agent、Workflow、Connector、Marketplace 等业务能力属于后续路线版本，当前版本尚未提供。

## 当前交付

- OpenEIP Engineering Process、RFC、ADR、版本与发布治理
- PRD、SAD、SDD Foundation 基线
- Java 21 + Spring Boot 健康服务
- Python 3.12 + FastAPI 健康服务
- React + Vite 前端壳
- Docusaurus 文档站
- Docker Compose 本地运行入口
- GitHub Actions 构建、测试、格式和安全检查

## 快速开始

前置条件：Docker Desktop 或 Docker Engine + Compose Plugin。

```bash
git clone https://github.com/CoderDongHuang/OpenEIP.git
cd openeip
docker compose up --build
```

启动后访问：

- 应用入口：<http://localhost:3000>
- Java 信息接口：<http://localhost:3000/api/v1/platform/info>
- Python 健康检查：<http://localhost:3000/ai/health>

## 本地开发

| 模块 | 要求 | 入口 |
|---|---|---|
| Java | JDK 21 | `cd java/platform && ./gradlew :platform-auth:bootRun` |
| Python | Python 3.12 | `cd python && pip install -e ".[dev]"` |
| Frontend | Node.js 22.12+ | `cd frontend && npm ci && npm run dev` |
| Website | Node.js 20+ | `cd website && npm ci && npm start` |

详细命令见各模块 README。

## 目标架构

```text
Browser -> Gateway -> Java Platform -> Python AI Engine
                         |                    |
                    Domain state       AI execution
                         \------ Event Bus --/
```

数据库、Kafka、向量存储和插件运行时需通过 Phase 1.5 Spike 后才会进入默认部署。

## 项目结构

```text
OpenEIP/
├── docs/           # 治理、产品、架构和设计文档
├── java/           # Java Platform
├── python/         # Python AI Engine
├── frontend/       # React 管理界面
├── deploy/         # 容器与网关配置
├── website/        # Docusaurus 文档站
├── sdk/            # 后续版本 SDK
├── plugins/        # 后续版本插件
├── benchmark/      # 性能基准
├── examples/       # 示例
├── scripts/        # 工程脚本
└── tools/          # 辅助工具
```

## 路线与贡献

- [Roadmap](ROADMAP.md)
- [工程流程](OEP.md)
- [贡献指南](CONTRIBUTING.md)
- [安全策略](SECURITY.md)
- [行为准则](CODE_OF_CONDUCT.md)

## 许可证

OpenEIP 使用 [Apache License 2.0](LICENSE)。
