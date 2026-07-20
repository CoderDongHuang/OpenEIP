# Coding Standard（编码规范）

> 本文档定义 OpenEIP 各技术栈的编码规范和所使用的代码风格工具。

---

## Java 编码规范

### 代码风格

遵循 **Google Java Style Guide**，通过以下工具强制执行：

| 工具 | 用途 | 配置文件 |
|---|---|---|
| **Checkstyle** | 代码风格检查 | `java/platform/config/checkstyle/checkstyle.xml` |
| **Spotless** | 代码自动格式化 | `java/platform/build.gradle.kts` |
| **SpotBugs** | 静态分析（Bug 检测） | CI 集成 |

### 命名约定

| 元素 | 规范 | 示例 |
|---|---|---|
| 包名 | 全小写，点分隔 | `com.openeip.knowledge` |
| 类名 | UpperCamelCase | `DocumentParser` |
| 接口名 | UpperCamelCase，不加 `I` 前缀 | `ConnectorService` |
| 方法名 | lowerCamelCase | `parseDocument()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_CHUNK_SIZE` |
| 变量 | lowerCamelCase | `chunkCount` |

### DDD 分层规范

```
com.openeip.<module>/
├── api/            ← REST Controller + DTO
├── application/    ← Application Service + Event Handler
├── domain/         ← Entity / Value Object / Domain Service / Repository Interface
├── infrastructure/ ← Repository Impl / External Client / Config
└── shared/         ← 共享常量 / 工具类
```

### 其他规范

- 禁止使用 `System.out.println`，统一使用 SLF4J
- 禁止空的 catch 块
- 公共 API 必须写 Javadoc
- 禁止使用 `*` 导入

---

## Python 编码规范

### 代码风格

| 工具 | 用途 | 配置文件 |
|---|---|---|
| **Ruff** | Lint + Format | `python/pyproject.toml` |

### 命名约定

| 元素 | 规范 | 示例 |
|---|---|---|
| 模块名 | snake_case | `document_parser.py` |
| 类名 | PascalCase | `DocumentParser` |
| 函数名 | snake_case | `parse_document()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_CHUNK_SIZE` |
| 变量 | snake_case | `chunk_count` |
| 私有成员 | 前缀 `_` | `_internal_cache` |

### 项目结构规范

```
<module>/
├── api/            ← FastAPI Router + Schema
├── application/    ← Use Case / Service
├── domain/         ← Entity / Value Object / Repository Protocol
├── infrastructure/ ← Repository Impl / External Client / Config
└── shared/         ← 共享常量 / 工具函数
```

### 其他规范

- 使用 Type Hints（所有公共函数和类方法）
- 使用 `logging` 模块，禁止 `print()`
- 公共 API 写 Docstring（Google 风格）
- 异步 IO 优先（FastAPI async）

---

## Frontend 编码规范

### 代码风格

| 工具 | 用途 | 配置文件 |
|---|---|---|
| **ESLint** | 代码质量检查 | `frontend/eslint.config.js` |
| **Prettier** | 代码格式化 | `frontend/.prettierrc.json` |

### 命名约定

| 元素 | 规范 | 示例 |
|---|---|---|
| 组件文件 | PascalCase | `KnowledgeBase.tsx` |
| 工具文件 | camelCase | `formatDate.ts` |
| 组件名 | PascalCase | `KnowledgeBase` |
| 函数/变量 | camelCase | `handleUpload` |
| 常量 | UPPER_SNAKE_CASE | `MAX_FILE_SIZE` |
| CSS 类名 | kebab-case | `.knowledge-base` |

### 项目结构规范

```
src/
├── components/     ← 通用组件
├── pages/          ← 页面组件
├── hooks/          ← 自定义 Hooks
├── services/       ← API 调用
├── stores/         ← 状态管理
├── types/          ← TypeScript 类型
└── utils/          ← 工具函数
```

---

## 通用规范（所有语言）

- UTF-8 编码，LF 换行符
- 缩进：Java 遵循 Google Java Format，Python 4 空格，Frontend 2 空格
- 文件末尾必须有且仅有一个空行
- 删除行尾空白字符
- 单行不超过 120 字符

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1.0 | 2026-07-20 | 初始版本 |
