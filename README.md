# OpenEIP

> Open Enterprise Intelligence Platform

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-v0.3.0--alpha-orange.svg)](CHANGELOG.md)

OpenEIP 是一个面向企业 AI 场景的开源平台项目。当前开发列车为 `v0.3.0-alpha` Knowledge，在 v0.2 MVP 基础上交付 PDF/Office 解析、生产 Embedding、全文/向量/混合检索与可定位 Citation。

该版本用于单节点内部验证。开发环境仍可使用确定性 Provider，但默认 Compose 已使用 Milvus 持久向量检索和 Elasticsearch 全文检索；生产模式拒绝确定性 Embedding 与内存检索适配器。多机部署、高可用、跨存储自动对账和真实模型质量评估不属于本次 alpha 承诺。

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

默认部署包含 hardened MySQL 8.4.10、Milvus 2.6、Elasticsearch 8.19、etcd 与 MinIO。PDF、DOCX、PPTX、XLSX、TXT 和 OCR 结果可进入受限解析链路，Knowledge 页支持全文、向量与混合检索，Chat Citation 可定位到原文摘录、页码和字符范围。

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
