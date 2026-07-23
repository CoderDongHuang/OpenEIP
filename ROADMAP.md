# Roadmap

## Release Train

```
v0.1 ──→ Foundation          已发布 alpha
         仓库 / 官网 / CI / 三份基线文档 / 脚手架

v0.2 ──→ MVP                 alpha 已发布，修订中
         登录 / RBAC / 文件上传 / OCR / 知识库 / RAG / Chat / Agent

v0.3 ──→ Knowledge           规划中
         文档解析 / Embedding / 全文检索 / 向量检索 / Citation

v0.4 ──→ Workflow            规划中
         Node / Canvas / Execution / Trigger / Approval / Retry

v0.5 ──→ Connector           规划中
         MySQL / PostgreSQL / Kafka / Git / 飞书 / 企业微信 / Email

v0.6 ──→ Agent               规划中
         Tool / Memory / Planner / Multi-Agent / MCP / Evaluation

v0.7 ──→ Governance          规划中
         多租户 / 审计 / 模型管理 / Prompt 管理 / 成本 / Trace

v0.8 ──→ Marketplace         规划中
         Plugin / SDK / Connector Market / Agent Market

v0.9 ──→ Performance         规划中
         压测 / 调优 / 高可用 / 扩容 / 稳定性

v1.0 ──→ LTS                 规划中
         SSO / LDAP / K8S / 对象存储 / 安全合规 / 24 个月支持
```

## 当前阶段

| 阶段 | 状态 | 发布条件 |
|---|---|---|
| Phase -1：Project Governance | 已完成 | 治理文档、初始维护机制、RFC/ADR 流程可执行 |
| Phase 0：Repository Bootstrap | 已完成 | 基础文件、CI、官网和仓库骨架可构建 |
| Phase 1：Architecture Baseline | 已完成 | PRD/SAD/SDD 与脚手架通过本地发布验证；首次 PR 需再通过 CI |
| Phase 1.5：Technical Validation | 已完成 | 5 个 Spike 已形成可复现数据，ADR-0004 已接受 |
| Phase 2：MVP Development | alpha 已发布，修订中 | `v0.2.0-alpha` 已于 2026-07-22 发布；Issue #63 正在补齐恢复路径、权限校验、工作台体验与发布证据 |

## 当前发布边界

- `v0.1.0-alpha` Foundation 已于 2026-07-21 发布；后续能力由 v0.2 版本列车承接。
- `v0.2.0-alpha` MVP 已于 2026-07-22 作为 GitHub Pre-release 发布；已公开标签保持不变。
- `v0.2.0-alpha` 仅承诺单节点、确定性 Provider 和内存向量库范围内的可验证 MVP。
- `v0.3.0` 将生产化文档解析与 Embedding，并交付全文检索、Milvus 向量检索和完整 Citation 能力。

## LTS 策略

- 每年发布一个 LTS 版本
- LTS 版本提供 24 个月长期支持
- 详见 [docs/00-governance/release-policy.md](docs/00-governance/release-policy.md)
