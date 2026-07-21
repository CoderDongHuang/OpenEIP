# Enterprise AI Platform 软件架构设计说明书 (SAD)

> 文档版本：1.1 | 产品基线：Unreleased (Phase 1.5) | 日期：2026-07-21 | 状态：Accepted Technical Baseline
>
> 本文档是 OpenEIP 项目的唯一软件架构基线，所有模块设计和开发必须依据本文档。

---

## 第一章：总体架构

### 1.1 架构全景

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Browser / Mobile                          │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │ HTTPS / WSS
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Nginx / Gateway                             │
│                    (路由 / 限流 / 负载均衡 / SSL)                      │
└─────────────────────────────────┬───────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     Java Platform (Spring Boot)                     │
│                                                                      │
│  ┌──────────┐ ┌──────────┐ ┌───────────┐ ┌──────────────────────┐ │
│  │  Auth    │ │Connector │ │ Workflow   │ │    Governance        │ │
│  │  Service │ │ Manager  │ │  Engine    │ │  (Audit/Tenant/Cost) │ │
│  └──────────┘ └──────────┘ └───────────┘ └──────────────────────┘ │
│                                                                      │
│  端口：8080                                                          │
└────────────┬──────────────────────────────────┬────────────────────┘
             │ REST / gRPC (同步)                │ Kafka (异步)
             ▼                                  ▼
┌─────────────────────────┐     ┌────────────────────────────────────┐
│   Python AI Engine      │     │         Event Bus (Kafka)           │
│   (FastAPI)             │     │                                     │
│                          │     │  Topic: document.lifecycle.parsed   │
│  ┌────────────────────┐ │     │  Topic: embedding.job.completed     │
│  │ Document Pipeline  │ │     │  Topic: workflow.instance.triggered │
│  │ (OCR/Parse/Chunk)  │ │     │  Topic: agent.task.completed        │
│  └────────────────────┘ │     │  Topic: connector.sync.completed    │
│  ┌────────────────────┐ │     │                                     │
│  │ Knowledge Engine   │ │     └────────────────────────────────────┘
│  │ (RAG/Embed/Search) │ │
│  └────────────────────┘ │
│  ┌────────────────────┐ │
│  │ Agent Runtime      │ │
│  │ (Plan/Exec/Memory) │ │
│  └────────────────────┘ │
│  ┌────────────────────┐ │
│  │ AI Node Runtime    │ │
│  │ (Node/Tool)        │ │
│  └────────────────────┘ │
│  ┌────────────────────┐ │
│  │ LLM Adapter        │ │
│  │ (Multi-Model/SSE)  │ │
│  └────────────────────┘ │
│                          │
│  端口：8000              │
└────────────┬─────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Storage Layer                               │
│                                                                      │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌────────┐ ┌──────────┐    │
│  │  MySQL  │ │  Redis  │ │   ES     │ │ Milvus │ │  Neo4j   │    │
│  │ (OLTP)  │ │ (Cache) │ │ (Search) │ │(Vector)│ │ (Graph)  │    │
│  └─────────┘ └─────────┘ └──────────┘ └────────┘ └──────────┘    │
│                                                                      │
│  ┌──────────┐ ┌─────────┐                                           │
│  │ClickHouse│ │  MinIO  │                                           │
│  │  (OLAP)  │ │ (Object)│                                           │
│  └──────────┘ └─────────┘                                           │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 架构决策

| 决策 | 选择 | 状态 | 记录 |
|---|---|---|---|
| 后端主语言 | Java + Python | Accepted | [ADR-0001](../12-adr/adr-0001-java-python-runtime.md) |
| 同步与异步通信 | REST/gRPC + Kafka + SSE | Accepted | [ADR-0002](../12-adr/adr-0002-communication-boundaries.md) |
| 服务形态 | Java 模块化平台 + 独立 Python AI Engine | Accepted | [ADR-0001](../12-adr/adr-0001-java-python-runtime.md) |
| 数据与检索组件 | 按用途验证引入，默认部署暂不包含 | Accepted with Conditions | [ADR-0003](../12-adr/adr-0003-storage-baseline.md) |
| 容器化 | Docker Compose 起步，K8S 后续验证 | Partially Accepted | [RFC-0001](../11-rfc/rfc-0001-foundation-architecture.md) |

### 1.3 关键设计原则

1. **读写分离**：Java 负责写（事务、一致性），Python 负责读和分析（AI 处理）
2. **事件驱动解耦**：Java 和 Python 通过 Kafka 异步解耦，各自独立演进
3. **同步调用有明确边界**：gRPC 仅用于低延迟的实时 AI 调用（如 Agent 对话）
4. **扩展契约先行**：Connector、Agent、Workflow Node 在实现前通过 RFC 固化 SPI
5. **无状态服务**：所有服务无状态，会话通过 Redis 共享

### 1.4 Phase 1.5 验证基线

| 技术假设 | 实测结论 | 架构边界 |
|---|---|---|
| Java ↔ Python gRPC | 通过 | 仅用于内部实时调用；生产实现补 TLS、deadline、错误映射和追踪 |
| Kafka Eventing | 通过 | 至少一次投递；`eventId` 幂等；3 次失败进入 DLQ |
| Milvus Vector Search | 有条件通过 | Phase 3 候选；真实语料和容量验证前不进入默认部署 |
| MCP Runtime | 通过 | 使用官方 MCP SDK；远程认证和租户隔离另行设计 |
| Browser LLM Streaming | 通过 | Gateway 到 Browser 使用 SSE；关闭代理缓冲并支持取消 |

完整数据与限制见 [ADR-0004](../12-adr/adr-0004-spike-validation-decisions.md) 和 [`spike/`](../../spike/README.md)。

---

## 第二章：Java 架构

### 2.1 DDD 分层架构

```
com.openeip.<module>/
├── api/                    ← 接口层
│   ├── controller/        ← REST Controller
│   ├── dto/               ← 请求/响应 DTO
│   └── websocket/         ← WebSocket 端点
│
├── application/            ← 应用层
│   ├── service/           ← 应用服务
│   ├── event/             ← 事件处理器（Kafka Consumer）
│   └── scheduler/         ← 定时任务
│
├── domain/                 ← 领域层
│   ├── entity/            ← 实体
│   ├── valueobject/       ← 值对象
│   ├── service/           ← 领域服务
│   └── repository/        ← 仓储接口
│
├── infrastructure/         ← 基础设施层
│   ├── repository/        ← 仓储实现（JPA/MyBatis）
│   ├── event/             ← 事件发布（Kafka Producer）
│   ├── client/            ← 外部服务客户端（gRPC/HTTP）
│   └── config/            ← 配置类
│
└── shared/                 ← 共享模块
    ├── constant/           ← 常量
    ├── exception/          ← 异常定义
    └── util/               ← 工具类
```

### 2.2 模块划分

```
java/
├── platform-gateway/       ← API Gateway
│   功能：路由转发、限流、认证、请求日志
│   技术：Spring Cloud Gateway
│
├── platform-auth/          ← 认证授权模块
│   功能：登录、JWT、RBAC、OAuth2/OIDC、SSO
│   技术：Spring Security + OAuth2
│
├── platform-connector/     ← 连接器管理模块
│   功能：Connector SPI、连接生命周期管理、元数据同步
│   技术：自定义 SPI + 插件类加载器
│
├── platform-workflow/      ← 工作流控制平面
│   功能：Workflow 定义、状态机、审批、重试与事务边界
│   技术：自研轻量级引擎（参考 Temporal）
│
├── platform-governance/    ← 治理模块
│   功能：多租户、审计、模型管理、Prompt 管理、成本统计
│   技术：Spring AOP（审计）+ 自定义拦截器
│
├── platform-search/        ← 搜索服务模块
│   功能：跨源搜索编排、权限过滤、结果聚合
│   技术：Elasticsearch Client
│
└── platform-common/        ← 共享模块
    功能：通用工具类、异常定义、Event Schema
```

### 2.3 Java ↔ Python 通信

#### REST（常规调用）

- Java 调用 Python：通过 HTTP Client（WebClient）调用 FastAPI
- Python 回调 Java：通过 HTTP 调用 Java REST API

#### gRPC（高性能调用）

- 场景：Agent 对话、Streaming 场景（低延迟要求）
- 定义：`.proto` 文件在 Java 和 Python 之间共享
- Java 侧：gRPC Client（net.devh:grpc-client-spring-boot-starter）
- Python 侧：gRPC Server（grpcio）

#### Kafka（异步事件）

- Java 发布事件 → Kafka Topic → Python 消费
- Python 发布事件 → Kafka Topic → Java 消费
- Event Schema：统一在语言无关的 `contracts/events/` 中以 JSON Schema 或 Protobuf 定义；Java/Python 生成绑定代码
- 交付语义：至少一次；Consumer 使用持久化 `eventId` 幂等键；业务失败 3 次后写入 `<topic>.dlq`

### 2.4 事务管理

- 使用 Spring `@Transactional`
- 分布式事务：Saga 模式（通过 Kafka 事件编排）
- 事件溯源：关键业务事件持久化到 Event Store

---

## 第三章：Python 架构

### 3.1 项目结构

```
python/
├── engine-core/            ← 核心共享模块
│   ├── config/            ← 配置管理
│   ├── logging/           ← 日志配置
│   └── models/            ← 共享数据模型
│
├── engine-document/        ← 文档处理引擎
│   ├── parser/            ← 文档解析器（PDF/Word/Excel/PPT）
│   ├── ocr/               ← OCR 引擎
│   ├── chunker/           ← 文档分块策略
│   └── pipeline/          ← 处理流水线
│
├── engine-knowledge/       ← 知识引擎
│   ├── embedding/         ← Embedding 生成
│   ├── vector_store/      ← 向量存储（Milvus）
│   ├── search/            ← 混合搜索（向量 + 全文）
│   ├── rag/               ← RAG 检索增强生成
│   └── graph/             ← 知识图谱（Neo4j）
│
├── engine-agent/           ← Agent 运行时
│   ├── runtime/           ← Agent 执行引擎
│   ├── tool/              ← Tool Calling 框架
│   ├── memory/            ← 短期 + 长期记忆
│   ├── planner/           ← 任务规划器
│   ├── reflection/        ← 反思和纠错
│   └── multi_agent/       ← 多 Agent 编排
│
├── engine-workflow/        ← AI Node Runtime
│   ├── executor/          ← 无状态 AI 节点执行器
│   └── nodes/             ← AI 节点实现；不持有工作流状态机
│
├── engine-mcp/             ← MCP Runtime
│   ├── server/            ← MCP Server
│   ├── client/            ← MCP Client
│   └── protocol/          ← 协议适配
│
├── engine-llm/             ← LLM 适配层
│   ├── adapter/           ← 多模型适配器（OpenAI/Claude/Qwen/DeepSeek）
│   ├── streaming/         ← SSE Streaming
│   └── cache/             ← LLM 响应缓存
│
└── engine-evaluation/      ← AI 评估
    ├── rag_eval/          ← RAG 评估（RAGAS）
    ├── agent_eval/        ← Agent 评估
    └── metrics/           ← 评估指标
```

### 3.2 文档处理流水线

```
文件上传
    │
    ▼
格式检测 (MIME Type)
    │
    ├── PDF → PyMuPDF / pdfplumber
    ├── Word → python-docx
    ├── Excel → openpyxl
    ├── PPT → python-pptx
    ├── Image → OCR (PaddleOCR / Tesseract)
    └── Text → 直接处理
    │
    ▼
文本提取
    │
    ▼
Chunk 分割 (Fixed / Semantic / Recursive)
    │
    ▼
Embedding 生成 (text-embedding-3-large / bge-large-zh)
    │
    ▼
存入 Milvus + Elasticsearch
    │
    ▼
发布事件 → Kafka Topic: document.lifecycle.parsed
```

### 3.3 RAG 检索流程

```
用户提问
    │
    ▼
Query 重写 (LLM → 多角度改写)
    │
    ▼
混合检索
    ├── 向量检索 (Milvus) → Top K 语义相关
    └── 全文检索 (ES) → Top K 关键词匹配
    │
    ▼
融合排序 (RRF / BGE Reranker)
    │
    ▼
Context 构建 (Top N Chunks + Metadata)
    │
    ▼
LLM 生成 (带引用的答案)
    │
    ▼
返回：答案 + 引用来源
```

---

## 第四章：数据架构

### 4.1 存储矩阵

| 存储 | 用途 | 数据特征 |
|---|---|---|
| **MySQL 8.0** | 核心业务数据（用户/角色/租户/工作流/审计） | OLTP、事务性、关系型 |
| **Redis 7** | 缓存、Session、分布式锁、限流计数器 | 内存、KV、TTL |
| **Elasticsearch 8** | 文档全文检索、日志搜索 | 倒排索引、非结构化 |
| **Milvus 2.x** | 向量存储（Embedding 检索） | 高维向量、ANN 搜索 |
| **Neo4j 5** | 知识图谱（实体关系） | 图数据、关联查询 |
| **ClickHouse** | BI 分析数据（未来） | OLAP、列式存储、聚合 |
| **MinIO** | 对象存储（文件/图片/模型） | 非结构化、大文件、S3 API |

### 4.2 MySQL 核心表设计（概念级）

```
用户与权限：
├── users                    ← 用户表
├── roles                    ← 角色表
├── permissions              ← 权限表
├── user_roles               ← 用户-角色关联
├── role_permissions         ← 角色-权限关联
├── tenants                  ← 租户表
└── organizations            ← 组织表

知识库：
├── knowledge_bases          ← 知识库
├── documents                ← 文档
├── chunks                   ← 文档块
├── embeddings               ← Embedding 元数据（向量在 Milvus）
└── tags                     ← 标签

连接器：
├── connectors               ← 连接器实例
├── connector_configs        ← 连接配置
└── sync_jobs                ← 同步任务

工作流：
├── workflows                ← 工作流定义
├── workflow_nodes           ← 节点定义
├── workflow_instances       ← 运行实例
└── workflow_tasks           ← 任务记录

Agent：
├── agent_definitions        ← Agent 定义
├── agent_sessions           ← Agent 会话
└── agent_messages           ← Agent 消息

审计：
├── audit_logs               ← 审计日志
├── api_access_logs          ← API 访问日志
└── ai_call_logs             ← AI 调用日志
```

### 4.3 数据访问规范

- 所有表必须有 `id`（UUID/BigInt）、`created_at`、`updated_at`
- 软删除使用 `deleted_at`（可空时间戳）
- 租户隔离：所有业务表包含 `tenant_id` 字段
- 索引命名：`idx_<table>_<column>`
- 唯一约束命名：`uk_<table>_<column>`
- 外键不使用物理外键约束，通过应用层保证一致性

---

## 第五章：消息架构

### 5.1 Kafka Topic 设计

| Topic | 生产者 | 消费者 | 事件内容 |
|---|---|---|---|
| `document.lifecycle.parsed` | Python | Java, Python | 文档解析完成通知 |
| `embedding.job.completed` | Python | Java | Embedding 生成完成 |
| `connector.sync.completed` | Java | Python | 连接器同步完成 |
| `connector.metadata.changed` | Java | Python | 元数据变更通知 |
| `workflow.instance.triggered` | Java | Python | AI 节点任务触发 |
| `workflow.node.completed` | Python | Java | 工作流节点完成 |
| `agent.task.created` | Java | Python | Agent 任务创建 |
| `agent.task.completed` | Python | Java | Agent 任务完成 |
| `audit.record.created` | Java, Python | Java | 审计日志收集 |
| `notification.message.requested` | Java | Java | 通知发送请求 |

### 5.2 事件模型规范

```json
{
  "eventId": "uuid",
  "eventType": "document.lifecycle.parsed",
  "timestamp": "2026-07-20T10:00:00Z",
  "source": "python-engine-document",
  "tenantId": "tenant-001",
  "userId": "user-001",
  "traceId": "trace-xxx",
  "payload": {
    "documentId": "doc_123"
  }
}
```

### 5.3 可靠性保障

- **至少一次投递**：Producer 使用 `acks=all`
- **幂等消费**：Consumer 通过 `eventId` 去重
- **死信队列**：消费失败 3 次后进入 DLQ（`<topic>.dlq`）
- **事件回溯**：Kafka 保留 7 天，关键事件持久化到 MySQL Event Store

---

## 第六章：AI 架构

### 6.1 AI 能力全景

```
┌──────────────────────────────────────────────────────────┐
│                      AI Capabilities                     │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │   LLM    │  │Embedding │  │  Rerank  │  │  Speech │ │
│  │ Gateway  │  │ Service  │  │ Service  │  │ Service │ │
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │   Tool   │  │  Memory  │  │   RAG    │  │  Graph  │ │
│  │ Calling  │  │ Manager  │  │ Pipeline │  │  RAG    │ │
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │  Agent   │  │   MCP    │  │ Multi-   │  │   Eval  │ │
│  │ Runtime  │  │ Runtime  │  │  Agent   │  │ Engine  │ │
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │
└──────────────────────────────────────────────────────────┘
```

### 6.2 LLM 适配层

支持多模型接入，统一接口：

```
                    ┌─────────────┐
                    │  LLM Router │
                    └──────┬──────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │  OpenAI  │    │  Claude  │    │ Local LLM│
    │ (GPT-4o) │    │(Opus 4.8)│    │(Qwen/DS) │
    └──────────┘    └──────────┘    └──────────┘

Router 策略：
- 按任务路由：代码生成 → Claude, 中文任务 → Qwen
- 按成本路由：简单任务 → 便宜模型
- 按延迟路由：实时 → 快速模型
- 故障转移：主模型不可用 → 备用模型
```

### 6.3 Agent 架构

```
                    ┌─────────────────┐
                    │   Agent Runtime │
                    └────────┬────────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ▼                ▼                ▼
    ┌───────────┐    ┌───────────┐    ┌───────────┐
    │  Planner  │    │ Executor  │    │Reflection │
    │ (思考)    │    │ (执行)    │    │ (反思)    │
    └─────┬─────┘    └─────┬─────┘    └─────┬─────┘
          │               │               │
          │         ┌─────┴─────┐         │
          │         │           │         │
          ▼         ▼           ▼         ▼
    ┌──────┐  ┌──────┐   ┌──────┐  ┌──────┐
    │ Tool │  │Memory│   │  LLM │  │  MCP │
    │ Call │  │      │   │ Call │  │ Tool │
    └──────┘  └──────┘   └──────┘  └──────┘

执行循环 (ReAct / Plan-Execute)：
1. Planner：分析任务 → 制定计划（步骤序列）
2. Executor：逐步执行 → Tool Calling → LLM
3. Reflection：检查结果 → 如有偏差 → 修正计划
4. 循环直到任务完成
```

### 6.4 MCP 架构

```
┌──────────────────────────────────────┐
│           MCP Runtime                │
│                                      │
│  ┌────────────┐  ┌────────────────┐ │
│  │ MCP Server │  │  MCP Client    │ │
│  │ (提供工具) │  │  (调用工具)    │ │
│  └────────────┘  └────────────────┘ │
│                                      │
│  OpenEIP 既是 MCP Server：            │
│  - 暴露知识库搜索 → 其他 Agent 可调用  │
│  - 暴露 Workflow 触发 → 外部可触发    │
│                                      │
│  也是 MCP Client：                    │
│  - 调用外部 MCP Server（数据库/API）  │
│  - 将外部工具注册为内部 Agent Tool    │
└──────────────────────────────────────┘
```

MCP Runtime 必须基于官方 SDK 的标准初始化、能力发现和工具调用语义实现。Phase 1.5 已验证 stdio Transport；远程 Transport、认证授权、租户隔离和审计必须在模块 SDD 中定义后才能发布。

---

## 第七章：权限架构

### 7.1 RBAC + ABAC 混合模型

```
┌─────────────────────────────────────────────┐
│              权限模型                         │
│                                              │
│  ┌─────────┐     ┌─────────┐                │
│  │  RBAC   │     │  ABAC   │                │
│  │ (角色)  │  +  │ (属性)  │                │
│  └────┬────┘     └────┬────┘                │
│       │               │                      │
│       ▼               ▼                      │
│  ┌──────────────────────────┐               │
│  │      权限决策引擎         │               │
│  │                          │               │
│  │  用户角色 → 权限列表      │               │
│  │  + 资源属性 → 额外约束    │               │
│  │  + 租户上下文 → 数据范围  │               │
│  └──────────────────────────┘               │
└─────────────────────────────────────────────┘
```

### 7.2 权限层级

| 层级 | 说明 | 示例 |
|---|---|---|
| **租户级** | 租户间的数据隔离 | Tenant A 看不到 Tenant B 的数据 |
| **组织级** | 组织内的数据范围 | 部门 A 看不到部门 B 的知识库 |
| **角色级** | 基于角色的功能权限 | Admin 可以管理用户，Viewer 只能查看 |
| **资源级** | 基于资源类型的权限 | 可以访问知识库 A 但不能访问知识库 B |
| **字段级** | 基于字段的权限 | 可以看到客户名称但不能看到联系方式 |
| **操作级** | 基于操作的权限 | 可以查看文档但不能删除文档 |

### 7.3 知识库权限

- 知识库可以设置访问级别：公开 / 组织内 / 指定用户
- 文档可以设置访问级别：继承知识库 / 自定义
- 文档中的敏感字段可以脱敏或隐藏

### 7.4 数据权限（行级安全）

- 数据库连接器查询时自动注入租户过滤条件
- 数据行级权限通过 SQL 拦截器实现（类似 Superset Row-Level Security）

---

## 第八章：插件架构

### 8.1 插件类型

| 插件类型 | SPI 接口 | 加载方式 | 隔离级别 |
|---|---|---|---|
| **Connector** | `ConnectorSpi` | 独立 ClassLoader | 强隔离 |
| **Agent** | `AgentSpi` | 独立 ClassLoader | 强隔离 |
| **Workflow Node** | `WorkflowNodeSpi` | 独立 ClassLoader | 强隔离 |
| **Tool** | `ToolSpi` | 动态注册 | 弱隔离 |
| **Theme** | `ThemeSpi` | 静态加载 | 无隔离 |

### 8.2 Connector SPI

```java
// Java 侧 SPI（插件开发者在 Java 中实现）
public interface ConnectorSpi {
    ConnectorMetadata getMetadata();
    List<ConfigField> getConfigSchema();
    TestResult testConnection(Config config);
    MetadataSchema extractMetadata(Config config);
    DataReader createReader(Config config);
    Optional<DataWriter> createWriter(Config config);
}
```

Connector 控制面 SPI 位于 Java。需要 Python 执行的数据处理能力通过版本化 RPC/Event 契约调用，不再定义第二套同名 Connector SPI。

### 8.3 插件生命周期

```
加载 → 验证 → 配置 → 激活 → 运行 → 停用 → 卸载
  │      │      │      │      │      │      │
  │      │      │      │      │      │      └─ 从 ClassLoader 移除
  │      │      │      │      │      └─ 断开连接、释放资源
  │      │      │      │      └─ 正常使用
  │      │      │      └─ 连接测试通过
  │      │      └─ 用户填写连接配置
  │      └─ 检查兼容性、签名验证
  └─ 从文件系统/Registry 加载 JAR/Whl
```

### 8.4 Marketplace

```
┌─────────────────────────────────────────┐
│            Plugin Marketplace           │
│                                         │
│  开发者 ──→ 上传插件 ──→ 审核 ──→ 发布  │
│                                         │
│  用户 ──→ 浏览 Market ──→ 安装 ──→ 使用 │
│                                         │
│  插件格式：                              │
│  connector-mysql-v1.0.0.jar             │
│  agent-sql-v1.0.0.whl                   │
│  workflow-approval-v1.0.0.zip           │
└─────────────────────────────────────────┘
```

---

## 第九章：部署架构

### 9.1 Docker Compose（Foundation）

```yaml
services:
  nginx:        # 反向代理
  java:         # Spring Boot (8080)
  python:       # FastAPI (8000)
  frontend:     # Nginx static frontend (80)
```

MySQL、Redis、Kafka、Elasticsearch、Milvus 和 MinIO 在对应 Spike 通过后以可选 Profile 加入，不属于 Foundation 默认启动面。

### 9.2 Kubernetes（生产/大规模）

```
┌─────────────────────────────────────────┐
│                 Ingress                 │
│              (Nginx / Traefik)          │
└─────────────────┬───────────────────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
    ▼             ▼             ▼
┌────────┐  ┌────────┐  ┌──────────┐
│  Java  │  │ Python │  │ Frontend │
│Deploy  │  │Deploy  │  │ Deploy   │
│(3 pods)│  │(3 pods)│  │ (2 pods) │
└────────┘  └────────┘  └──────────┘

数据库层使用 StatefulSet + PVC
MySQL / ES / Milvus / Neo4j → 使用 Helm Chart 或外部托管
```

### 9.3 高可用策略

| 组件 | HA 方案 |
|---|---|
| **Java** | 多副本 Deployment + HPA 自动伸缩 |
| **Python** | 多副本 Deployment + HPA 自动伸缩 |
| **MySQL** | 主从复制 / Group Replication / 云 RDS |
| **Redis** | Sentinel / Cluster |
| **Kafka** | 3 Broker 集群 |
| **Milvus** | 分布式部署（Proxy + Data + Index + Coordinator） |
| **MinIO** | 分布式模式（4+ 节点） |

---

## 第十章：安全架构

### 10.1 安全分层

```
┌───────────────────────────────────────┐
│  Layer 1：传输安全                    │
│  - TLS 1.3 全链路加密                 │
│  - HSTS 强制 HTTPS                    │
├───────────────────────────────────────┤
│  Layer 2：认证安全                    │
│  - JWT + OAuth2/OIDC                 │
│  - SSO 集成（LDAP/SAML）              │
│  - MFA（未来）                        │
├───────────────────────────────────────┤
│  Layer 3：授权安全                    │
│  - RBAC + ABAC                       │
│  - 行级数据安全（Row-Level Security） │
│  - 字段级数据安全                     │
├───────────────────────────────────────┤
│  Layer 4：AI 安全                     │
│  - Prompt Injection 检测和防护        │
│  - LLM 输入/输出过滤                  │
│  - 敏感信息脱敏                       │
│  - Hallucination 检测                 │
├───────────────────────────────────────┤
│  Layer 5：数据安全                    │
│  - 敏感字段加密（AES-256-GCM）        │
│  - 数据脱敏（手机/身份证/邮箱）        │
│  - Secret 管理（环境变量/Sealed Secrets）│
├───────────────────────────────────────┤
│  Layer 6：审计安全                    │
│  - 操作审计日志                       │
│  - API 访问日志                       │
│  - AI 调用全链路记录                  │
└───────────────────────────────────────┘
```

### 10.2 Prompt Injection 防护

```
用户输入
    │
    ▼
┌──────────────┐
│ 输入预处理    │ ← 检测已知注入模式
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 沙箱检测      │ ← LLM 分析是否包含注入意图
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 角色限定      │ ← System Prompt 中明确边界
└──────┬───────┘
       │
       ▼
    正常执行
```

---

## 第十一章：Observability

### 11.1 可观测性体系

```
┌────────────────────────────────────────────────┐
│              Observability Stack                │
│                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐ │
│  │ Metrics  │  │  Traces  │  │    Logs      │ │
│  │Prometheus│  │OpenTelem │  │ELK / Loki    │ │
│  └────┬─────┘  └────┬─────┘  └──────┬───────┘ │
│       │             │               │          │
│       └─────────────┼───────────────┘          │
│                     │                           │
│                     ▼                           │
│           ┌─────────────────┐                  │
│           │    Grafana      │                  │
│           │  (统一可视化)   │                  │
│           └─────────────────┘                  │
└────────────────────────────────────────────────┘
```

### 11.2 LLM Tracing

每个 LLM 调用记录：

- Trace ID（关联整个请求链路）
- 模型名称和版本
- Prompt Token / Completion Token / Total Token
- 延迟（首 Token 延迟 / 总延迟）
- 成本（按模型单价计算）
- 输入/输出内容摘要
- 错误和重试记录

### 11.3 关键 Metrics

| Category | Metrics |
|---|---|
| **API** | QPS、延迟 P50/P99、错误率 |
| **LLM** | Token 用量、成本、模型延迟 |
| **RAG** | 检索延迟、命中率、召回率 |
| **Agent** | 任务成功率、平均执行时间 |
| **Connector** | 同步速度、错误率 |
| **Infrastructure** | CPU/Memory/Disk/Network |

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1.1 | 2026-07-21 | 合并 Phase 1.5 实测通信、Milvus、MCP 与 SSE 决策 |
| v1.0 | 2026-07-20 | 初始 Baseline 版本 |
