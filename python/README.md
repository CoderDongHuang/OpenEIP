# Python AI Engine

FastAPI 项目——Agent、RAG、LLM、Embedding、文档解析、AI Workflow 编排。

## 技术栈

- Python 3.12
- FastAPI
- LangChain / LlamaIndex
- PydanticAI / LangGraph
- Milvus (向量存储)
- gRPC (与 Java Platform 通信)
- Kafka (事件消费与发布)

## 模块结构

```
python/
├── engine-core/        ← 共享模块（配置、日志、工具）
├── tests/              ← Foundation 测试
├── pyproject.toml
└── README.md
```

其他 AI 模块将在对应版本通过 RFC 和子 SDD 后创建。

## 本地运行

```bash
python -m venv .venv
.venv/Scripts/pip install -r requirements-dev.lock
.venv/Scripts/pip install --no-deps -e .
.venv/Scripts/uvicorn engine_core.main:app --reload
```

## 编码规范

- Type Hints（所有公共函数）
- Ruff Lint + Format
- Google Style Docstring
- FastAPI async 优先
- 详见 [docs/00-governance/coding-standard.md](../docs/00-governance/coding-standard.md)
