# Architecture Review: Issue #42 Auth 模块

> 审查日期: 2026-07-22
> 审查对象: Auth Sub-SDD 1.1、Auth OpenAPI 1.0、Auth Database Design 1.0、ADR-0005
> 结论: Approved for Implementation

## 审查结论

| 检查项 | 结论 | 说明 |
|---|---|---|
| SAD 模块边界 | 通过 | 仅实现既有 `platform-auth` 边界，不承担 Gateway、SSO 或多租户职责 |
| API First | 通过 | OpenAPI 在实现前定义 7 个端点、统一信封和错误响应 |
| 数据所有权 | 通过 | Auth 独占用户、角色、权限和 refresh token 状态 |
| 安全策略 | 通过 | RS256、外部密钥、数据库实时权限、单次 refresh rotation、账户禁用 |
| Migration | 通过 | 仅增表；提供 FK、索引和独立 rollback SQL；引用数据由应用初始化器负责 |
| 可观测性 | 通过 | 请求 ID、标准错误信封；认证失败不记录密码和 Token |
| 兼容性 | 通过 | 新增 `/api/v1/auth/**`，不修改已有 API、SDK 或 SPI |
| RFC | N/A | 模块和技术边界已在 SAD 接受，无新 SPI/通信架构 |
| ADR | 通过 | ADR-0005 记录安全策略及备选方案 |

## Coding 约束

- 生产启动时缺少 RSA 密钥或数据库凭据必须失败，不能回退到公开默认值。
- JWT claims 不作为最终授权数据源；Filter 必须加载当前用户状态和权限。
- refresh token 必须以 `jti` 哈希持久化，并在同一事务中消费和轮换。
- H2 只用于快速测试；Migration 和契约必须在真实 MySQL Testcontainers 上验证。
- Security Review、Benchmark 与六项 Quality Gate 在实测前保持 Pending。
