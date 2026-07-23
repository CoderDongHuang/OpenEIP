# OpenEIP

> Open Enterprise Intelligence Platform

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-v0.2.0--alpha-orange.svg)](CHANGELOG.md)

OpenEIP 是一个面向企业 AI 场景的开源平台项目。当前 `v0.2.0-alpha` 是 MVP 预发布版本，交付认证授权、文件控制面、受限文档处理、知识库、Embedding、RAG、流式 Chat 和约束 Agent。

该版本用于单节点内部验证。OCR、Embedding、RAG 和 Agent 默认使用确定性 Provider，向量数据位于进程内存，文件存储使用本地 Volume；真实模型、Milvus、Kafka 生产链路、多机部署和高可用不属于本次发布承诺。

## 当前交付

- OpenEIP Engineering Process、RFC、ADR、版本与发布治理
- PRD、SAD、SDD 及模块级设计、API、数据库和 SPI 契约
- Java 21 + Spring Boot：Auth/RBAC、文件、知识库、Chat 与 Agent 网关
- Python 3.12 + FastAPI：OCR、解析、Embedding、RAG、Chat 与 Agent Runtime
- React + Vite 六页操作工作台：Overview、Documents、Knowledge、Chat、Agents 与 Access
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

默认部署包含基于官方 8.4.10 LTS、digest 固定并移除非运行时管理工具的 hardened MySQL 镜像；Kafka、Milvus、远程 MCP 和真实模型 Provider 已完成技术验证，但将在后续版本通过生产化设计后进入默认部署。

v0.2 中 TXT 与 PNG/JPEG 可进入受限处理链路；PDF 仅支持安全存储和下载。向量数据位于
Python 进程内存，服务重启后可在 Knowledge 页对已就绪文档执行“重建向量”。

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
