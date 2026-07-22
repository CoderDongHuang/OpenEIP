# Enterprise AI Platform 设计基线 (SDD Baseline)

> 文档版本：1.4 | 产品基线：v0.2.0 MVP (In Development) | 日期：2026-07-22 | 状态：Accepted Technical Baseline
>
> 本文档定义 OpenEIP 项目的全局设计规范。各模块的详细设计在各自子 SDD 中展开。

---

## 第一章：模块划分与目录规范

### 1.1 顶层模块

```
OpenEIP/
├── docs/           ← 设计文档（00-governance ~ 15-roadmap）
├── java/           ← Java Platform：企业业务逻辑、权限、工作流引擎
├── python/         ← Python AI Engine：Agent、RAG、LLM、文档处理
├── frontend/       ← Web UI
├── deploy/         ← 部署配置
├── sdk/            ← 多语言 SDK
├── plugins/        ← 插件（Connector/Agent/Workflow Node）
├── benchmark/      ← 性能基准
├── website/        ← 文档站点
├── examples/       ← 示例项目
├── scripts/        ← 脚本工具
└── tools/          ← 辅助工具
```

### 1.2 模块职责边界

| 模块 | 负责 | 不负责 |
|---|---|---|
| **java/** | 企业业务逻辑、事务、权限、工作流状态机、Connector 控制面 | AI 推理、文档解析、LLM 调用 |
| **python/** | AI 推理、文档解析、LLM 调用、Agent 与 AI 节点执行、RAG | 权限决策、事务管理、工作流状态机 |
| **frontend/** | UI 渲染和交互 | 业务逻辑、AI 处理 |
| **deploy/** | 容器编排和部署配置 | 业务代码 |

### 1.3 模块通信契约

| 通道 | 协议 | 方向 | 用途 |
|---|---|---|---|
| Java → Python | REST HTTP | 请求/响应 | 常规 AI 调用 |
| Java → Python | gRPC | Streaming | Agent 对话、实时 AI |
| Python → Java | REST HTTP | 请求/响应 | 回调通知 |
| Java → Python | Kafka | 异步 | 事件通知（文档就绪、任务创建） |
| Python → Java | Kafka | 异步 | 事件通知（处理完成、状态变更） |

### 1.4 Phase 1.5 后的实现约束

- gRPC 契约必须从共享 `.proto` 生成，禁止手工复制模型；所有调用设置 deadline 并统一映射状态码。
- Kafka 使用至少一次投递，事件信封遵循第五章；Consumer 以持久化 `eventId` 去重，失败 3 次写入 `<topic>.dlq`。
- Gateway 到浏览器的生成式输出使用 SSE，事件至少包含 `token`、`done`、`error`；客户端取消必须向上游传播。
- Nginx SSE location 必须配置 `proxy_buffering off`、`proxy_cache off` 和足够的 `proxy_read_timeout`。
- MCP 使用官方 SDK 的标准生命周期；自定义 REST/JSON-RPC 模拟不视为 MCP 兼容实现。
- Milvus 仅是 Phase 3 候选组件，真实语料和容量验证完成前不得加入默认 Compose Profile。

依据：[ADR-0004](../12-adr/adr-0004-spike-validation-decisions.md)。

### 1.5 Java 模块组合约束

- `platform-app` 是 Java 控制面的唯一可部署组合入口，负责统一扫描、配置和 Migration。
- `platform-auth`、`platform-document` 等业务模块保持独立 Gradle 模块并可独立测试，不拆成独立网络服务。
- 公共响应信封和请求 ID Filter 位于 `platform-common`；业务模块不得复制全局 Web 契约。
- 二进制内容通过模块内部存储端口访问，API 参数和原始文件名不得作为对象路径。
- 依据：[RFC-0002](../11-rfc/rfc-0002-document-control-plane.md) 和
  [ADR-0006](../12-adr/adr-0006-file-storage-consistency.md)。

### 1.6 Python 内部 AI API 约束

- Python AI API 使用应用工厂装配模块，业务 Provider 通过 Domain Port 注入，不在 Router 中硬编码模型调用。
- Java/内部编排调用必须携带非空服务凭据、规范租户/用户 UUID 和安全请求 ID；未配置凭据时接口关闭失败。
- 二进制输入在解码前后分别执行字节、容器、尺寸、像素和帧数限制；声明 MIME 不代替实际容器验证。
- OCR/解析文本一律作为不可信数据，不得拼接进 system/developer Prompt 边界。
- v0.2 OCR 详细契约见 [OCR Sub-SDD](ocr-module.md) 和
  [OCR OpenAPI](../06-api/ocr-v1.openapi.yaml)。
- v0.2 解析结果必须是规范化文本的可验证切片；事件只携带标识、哈希、计数和幂等键。
  详细契约见 [Document Parsing Sub-SDD](document-parsing-module.md)。

---

## 第二章：包结构规范

### 2.1 Java DDD 分层

```
com.openeip.<module>/
├── api/                    ← 接口层（Controller、DTO、WebSocket）
│   ├── controller/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   └── websocket/
│
├── application/            ← 应用层（Service、EventHandler、Scheduler）
│   ├── service/
│   ├── event/
│   └── scheduler/
│
├── domain/                 ← 领域层（Entity、VO、DomainService、Repository接口）
│   ├── entity/
│   ├── valueobject/
│   ├── service/
│   └── repository/
│
├── infrastructure/         ← 基础设施层（Repo实现、Client、Config）
│   ├── repository/
│   ├── event/
│   ├── client/
│   └── config/
│
└── shared/                 ← 共享
    ├── constant/
    ├── exception/
    └── util/
```

**依赖规则**：
- `api` → `application` → `domain`
- `infrastructure` → `domain`（实现 domain 接口）
- `shared` 被所有层依赖
- `domain` 不依赖 `application` 和 `infrastructure`

### 2.2 Python 分层

```
engine_<module>/
├── api/                    ← FastAPI Router + Schema（Pydantic）
│   ├── routes/
│   └── schemas/
│
├── application/            ← 用例服务
│   ├── services/
│   └── events/
│
├── domain/                 ← 领域模型 + 仓储协议
│   ├── entities/
│   ├── value_objects/
│   └── repositories/
│
├── infrastructure/         ← 基础设施实现
│   ├── repositories/
│   ├── clients/
│   └── config/
│
└── shared/                 ← 共享
    ├── constants/
    ├── exceptions/
    └── utils/
```

### 2.3 Frontend 分层

```
src/
├── components/             ← 通用组件
├── pages/                  ← 页面组件
├── hooks/                  ← 自定义 Hooks
├── services/               ← API 调用层
├── stores/                 ← 状态管理
├── types/                  ← TypeScript 类型定义
└── utils/                  ← 工具函数
```

---

## 第三章：API 风格规范

### 3.1 URL 设计

```
/api/v1/<module>/<resource>

示例：
GET    /api/v1/knowledge/bases              ← 获取知识库列表
POST   /api/v1/knowledge/bases              ← 创建知识库
GET    /api/v1/knowledge/bases/{id}         ← 获取知识库详情
PUT    /api/v1/knowledge/bases/{id}         ← 更新知识库
DELETE /api/v1/knowledge/bases/{id}         ← 删除知识库
POST   /api/v1/knowledge/bases/{id}/sync    ← 触发同步（非标准 CRUD 操作用动词）
```

### 3.2 HTTP 方法语义

| 方法 | 语义 | 幂等 |
|---|---|---|
| GET | 查询 | ✅ |
| POST | 创建 / 非标准操作 | ❌ |
| PUT | 全量更新 | ✅ |
| PATCH | 部分更新 | ❌ |
| DELETE | 删除 | ✅ |

### 3.3 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": { },
  "requestId": "trace-uuid",
  "timestamp": "2026-07-20T10:00:00Z"
}
```

`204 No Content` 不返回响应体；文件流和 SSE 使用各自媒体类型，不套用 JSON 响应信封。

### 3.4 分页规范

**请求**：
```
GET /api/v1/knowledge/bases?page=1&pageSize=20&sort=createdAt&order=desc
```

**响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [ ],
    "page": 1,
    "pageSize": 20,
    "total": 100,
    "totalPages": 5
  }
}
```

### 3.5 错误码规范

```
格式：<模块前缀>-<错误类型>-<序号>

模块前缀：
AUTH  - 认证授权
DOC   - 文档处理
KB    - 知识库
AGENT - Agent
WF    - Workflow
CONN  - Connector
GOV   - Governance

错误类型：
E - 业务错误
V - 参数校验错误
P - 权限错误
S - 系统错误

示例：
AUTH-P-001  ← 认证模块，权限错误，序号 001（Token 过期）
DOC-V-002   ← 文档模块，校验错误，序号 002（文件格式不支持）
KB-E-003    ← 知识库模块，业务错误，序号 003（知识库不存在）
```

### 3.6 HTTP 状态码映射

| 场景 | 状态码 |
|---|---|
| 成功 | 200 OK / 201 Created / 204 No Content |
| 参数校验失败 | 400 Bad Request |
| 认证失败 | 401 Unauthorized |
| 权限不足 | 403 Forbidden |
| 资源不存在 | 404 Not Found |
| 冲突 | 409 Conflict |
| 系统错误 | 500 Internal Server Error |
| 服务不可用 | 503 Service Unavailable |

### 3.7 SSE 事件规范

用于 LLM Streaming 和实时通知：

```text
event: token
data: {"token": "你好", "index": 0}

event: token
data: {"token": "，", "index": 1}

event: done
data: {"totalTokens": 150, "finishReason": "stop"}

event: error
data: {"code": "LLM-S-001", "message": "模型调用超时"}
```

响应使用 `text/event-stream`、`Cache-Control: no-cache, no-transform` 和 `X-Accel-Buffering: no`。浏览器中止请求后，Gateway 与 Python 必须取消上游流；自动重试仅允许在尚未向用户输出 Token 且请求可证明幂等时执行。

### 3.8 WebSocket 规范

用于实时交互（Agent 对话、Workflow 状态推送）：

```
客户端 → 服务端：
{
  "type": "agent.chat",
  "sessionId": "uuid",
  "payload": { "message": "帮我分析上季度销售数据" }
}

服务端 → 客户端：
{
  "type": "agent.thinking",
  "sessionId": "uuid",
  "payload": { "step": "planning", "message": "正在分析任务..." }
}
```

---

## 第四章：数据库命名规范

### 4.1 表命名

```
格式：<module>_<entity>s

示例：
auth_users
auth_roles
knowledge_bases
knowledge_documents
connector_instances
workflow_definitions
agent_sessions
```

### 4.2 字段命名

- 全小写，下划线分隔（snake_case）
- 主键：`id`（UUID 字符串或 BIGINT 自增）
- 外键：`<referenced_table>_id`
- 时间戳：`created_at`、`updated_at`、`deleted_at`
- 布尔字段：`is_<adjective>`（如 `is_active`、`is_deleted`）
- 状态字段：`status`（使用 VARCHAR，不用 INT 枚举）
- 租户隔离：`tenant_id`

### 4.3 索引命名

```
主键：       pk_<table>
唯一索引：   uk_<table>_<column>[_<column>]
普通索引：   idx_<table>_<column>[_<column>]
```

### 4.4 Migration 规范

- 使用 Flyway（Java 侧）
- 文件命名：`V<version>__<description>.sql`（如 `V1.0.0__init_schema.sql`）
- 每个 Migration 必须可逆（提供 Undo SQL）
- 禁止在 Migration 中插入业务数据（种子数据使用独立脚本）

---

## 第五章：事件模型规范

### 5.1 Event Schema

```json
{
  "eventId": "evt_<uuid>",
  "eventType": "<domain>.<entity>.<action>",
  "eventVersion": "1.0",
  "timestamp": "ISO 8601 UTC",
  "source": {
    "service": "<service-name>",
    "instance": "<instance-id>"
  },
  "context": {
    "tenantId": "<tenant-id>",
    "userId": "<user-id>",
    "traceId": "<trace-id>"
  },
  "payload": {
    "example": "事件具体内容由 eventType 对应的独立 Schema 定义"
  }
}
```

### 5.2 Topic 命名

```
格式：<domain>.<entity>.<action>

示例：
document.lifecycle.parsed
embedding.job.completed
connector.sync.completed
workflow.instance.started
agent.task.completed
audit.record.created
```

### 5.3 事件版本

- 事件 Schema 变更时递增 `eventVersion`
- Consumer 必须支持旧版本事件的反序列化
- 新增字段必须是可选的
- 废弃字段通过 `deprecated` 标记

---

## 第六章：Plugin SPI 规范

### 6.1 Connector SPI

```java
// Java 侧 SPI — 插件 jar 放入 plugins/connectors/ 后自动加载
public interface ConnectorSpi {
    ConnectorMetadata getMetadata();           // 名称、版本、图标、描述
    List<ConfigField> getConfigSchema();       // 配置项定义
    TestResult testConnection(Config config);  // 连接测试
    MetadataSchema extractMetadata(Config config); // 元数据提取
    DataReader createReader(Config config);    // 数据读取器
    Optional<DataWriter> createWriter(Config config); // 数据写入器（可选）
}
```

Connector 控制面 SPI 只在 Java 定义。Python 数据处理使用版本化 RPC/Event 契约，避免同一插件出现两套不兼容 SPI。

### 6.2 Agent SPI

```python
# Python 侧 SPI — 插件 whl 安装后自动注册
class AgentSpi(ABC):
    @abstractmethod
    def get_metadata(self) -> AgentMetadata: ...

    @abstractmethod
    def get_tools(self) -> list[ToolDefinition]: ...

    @abstractmethod
    async def execute(self, context: AgentContext) -> AgentResult: ...
```

### 6.3 插件打包规范

```
connector-mysql-v1.0.0.jar
├── META-INF/
│   └── services/
│       └── com.openeip.spi.ConnectorSpi    ← SPI 声明文件
├── com/openeip/connector/mysql/             ← 插件代码
└── plugin.yaml                              ← 插件元数据

plugin.yaml：
name: connector-mysql
version: 1.0.0
type: connector
spiClass: com.openeip.connector.mysql.MySQLConnector
author: OpenEIP
description: MySQL 数据库连接器
dependencies:
  java: ">=21"
  platform: ">=0.2.0"
```

---

## 第七章：Agent 架构总览

### 7.1 Agent 类型

| Agent 类型 | 用途 | 关键 Tool |
|---|---|---|
| **Document Agent** | 文档处理、信息提取 | OCR、Parse、Summarize |
| **SQL Agent** | 自然语言查询数据库 | Execute SQL、Schema Query |
| **BI Agent** | 数据分析和可视化 | Chart、Dashboard、Report |
| **Search Agent** | 跨源信息检索 | ES Search、Vector Search |
| **Workflow Agent** | 多步骤任务自动化 | Workflow Trigger、Approval |
| **Coding Agent** | 代码生成和审查 | Code Gen、Code Review |
| **Meeting Agent** | 会议纪要、行动项提取 | Transcript、Summary |
| **Finance Agent** | 财务报表分析 | Finance Query、Forecast |
| **Legal Agent** | 合同审查、合规检查 | Contract Review、Clause Search |
| **HR Agent** | 简历筛选、入职流程 | Resume Parse、Onboarding |

### 7.2 Agent 执行模型

```
ReAct (Reasoning + Acting) 模式：

1. Thought：分析当前状态 → "我需要查询上季度销售数据"
2. Action：调用 Tool → Tool: SQL Query("SELECT ... FROM sales WHERE ...")
3. Observation：观察结果 → "查询返回了 500 条记录"
4. Thought：分析结果 → "数据已获取，需要生成趋势图表"
5. Action：调用 Tool → Tool: Generate Chart(data, type="line")
6. Observation：观察结果 → "图表已生成"
7. Final Answer：返回最终结果给用户
```

### 7.3 Agent Memory

| Memory 类型 | 存储 | TTL | 用途 |
|---|---|---|---|
| **短期记忆** | Redis | 会话结束 | 当前对话上下文 |
| **长期记忆** | Milvus | 永久 | 历史对话、用户偏好 |
| **工作记忆** | Python Dict | 单次任务 | 当前任务的中间结果 |

---

## 第八章：编码规范与命名约定

### 8.1 通用原则

- **UTF-8** 编码，**LF** 换行符
- 文件末尾有且仅有一个空行
- 删除行尾空白字符
- 缩进风格与项目保持一致

### 8.2 Java 命名

| 元素 | 规范 | 示例 |
|---|---|---|
| 包名 | 全小写 | `com.openeip.knowledge` |
| 类名 | UpperCamelCase | `DocumentParser` |
| 方法/变量 | lowerCamelCase | `parseDocument()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_CHUNK_SIZE` |
| 枚举 | UpperCamelCase | `DocumentStatus.READY` |

### 8.3 Python 命名

| 元素 | 规范 | 示例 |
|---|---|---|
| 模块/文件 | snake_case | `document_parser.py` |
| 类名 | PascalCase | `DocumentParser` |
| 函数/变量 | snake_case | `parse_document()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_CHUNK_SIZE` |
| 私有成员 | 前缀 `_` | `_internal_cache` |

### 8.4 Frontend 命名

| 元素 | 规范 | 示例 |
|---|---|---|
| 组件文件 | PascalCase | `KnowledgeBase.tsx` |
| 工具文件 | camelCase | `formatDate.ts` |
| 函数/变量 | camelCase | `handleUpload` |
| 常量 | UPPER_SNAKE_CASE | `MAX_FILE_SIZE` |
| CSS 类 | kebab-case | `.knowledge-base` |

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1.4 | 2026-07-22 | 增加解析结果切片可追溯性、重放键与无原文事件约束 |
| v1.3 | 2026-07-22 | 增加 Python 内部 AI API、OCR 输入资源限制和 Prompt 数据边界 |
| v1.2 | 2026-07-22 | 增加 Java 模块组合、共享 Web 契约与文件存储边界 |
| v1.1 | 2026-07-21 | 增加 Phase 1.5 后的 gRPC、Kafka、SSE、MCP 和 Milvus 约束 |
| v1.0 | 2026-07-20 | 初始 Baseline 版本 |
