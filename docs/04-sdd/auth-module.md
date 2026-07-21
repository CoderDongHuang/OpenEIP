# Auth 模块详细设计 (Sub-SDD)

> 版本: 1.1 | 日期: 2026-07-22 | 状态: Approved for Implementation
> Issue: [#42](https://github.com/CoderDongHuang/OpenEIP/issues/42) | ADR: [ADR-0005](../12-adr/adr-0005-auth-token-security.md)

## 1. 职责与边界

### 1.1 负责

- 用户注册、登录、禁用状态校验和当前用户查询。
- RS256 access/refresh token 签发与验证。
- refresh token 单次轮换、重放阻止和撤销状态持久化。
- 用户、角色和权限的 RBAC 管理。
- Auth 服务内的基础请求限流与标准认证错误响应。

### 1.2 不负责

- Gateway 全局路由、跨实例限流和 TLS 终止。
- OAuth2/OIDC/SSO、MFA、多租户和审计平台。
- 其他业务模块的数据权限策略。

### 1.3 依赖

```text
platform-auth
├── platform-common
├── Spring Security / Validation / Data JPA
├── JJWT (RS256)
└── MySQL + Flyway
```

## 2. API First

规范文件: [auth-v1.openapi.yaml](../06-api/auth-v1.openapi.yaml)

| 方法 | 路径 | 认证 | 权限 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 否 | 限流 |
| POST | `/api/v1/auth/login` | 否 | 限流 |
| POST | `/api/v1/auth/refresh` | refresh token | 限流 |
| GET | `/api/v1/auth/me` | access token | authenticated |
| GET | `/api/v1/auth/roles` | access token | `ROLE_ADMIN` |
| POST | `/api/v1/auth/roles` | access token | `ROLE_ADMIN` |
| PUT | `/api/v1/auth/users/{id}/roles` | access token | `ROLE_ADMIN` |

所有 JSON 响应使用全局信封 `code/message/data/requestId/timestamp`。认证失败返回 401，权限不足返回 403，限流返回 429。响应和日志不得回显密码、JWT 或 RSA 私钥。

## 3. Token 与认证流程

### 3.1 Key 配置

- `JWT_PRIVATE_KEY_BASE64`: PKCS#8 DER 私钥。
- `JWT_PUBLIC_KEY_BASE64`: X.509 DER 公钥。
- RSA modulus 不少于 2048 bits，私钥和公钥必须配对。
- `JWT_ALLOW_EPHEMERAL_KEY=false` 为默认值；只有测试或显式开发环境可设为 `true`。

### 3.2 Claims

| Claim | access | refresh |
|---|---|---|
| `iss=openeip` | 必须 | 必须 |
| `sub=userId` | 必须 | 必须 |
| `type` | `access` | `refresh` |
| `jti` | 随机 UUID | 随机 UUID |
| `iat/exp` | 必须 | 必须 |

角色和权限不作为最终授权来源。Filter 验证 access token 后按 `sub` 加载当前用户；用户不存在或禁用时返回 401，权限由当前数据库关系生成。

### 3.3 Refresh rotation

1. 解析 RS256 refresh token 并取得 `sub/jti/exp`。
2. 计算 `SHA-256(jti)`，使用悲观行锁查询 `auth_refresh_tokens`。
3. 校验 token 未使用、未撤销、未过期，且用户仍启用。
4. 在同一事务中标记旧记录 `used_at`，生成并保存新 token hash。
5. 任意旧 token 重放返回 `AUTH-E-003`。

## 4. RBAC

```text
auth_users --< auth_user_roles >-- auth_roles --< auth_role_permissions >-- auth_permissions
```

- 内置角色名为 `ROLE_ADMIN`、`ROLE_USER`、`ROLE_VIEWER`。
- Spring authority 直接使用数据库角色名，不再次添加 `ROLE_`。
- 新角色名必须匹配 `ROLE_[A-Z0-9_]+`。
- 用户角色替换至少保留一个有效角色。
- 角色创建和用户角色分配仅允许 `ROLE_ADMIN`。

## 5. 密码与限流

- BCrypt strength=12。
- 密码长度 8～72 字符，并且 UTF-8 编码不得超过 72 bytes。
- 登录失败统一返回“用户名或密码错误”，不暴露用户是否存在。
- 登录、注册和刷新按 `remoteAddr + endpoint` 使用有界固定窗口限流；响应包含 `Retry-After`。
- 多实例部署前将限流迁移到 Gateway + Redis，不依赖 `X-Forwarded-For` 等客户端可伪造 Header。

## 6. 数据库

完整定义: [auth-schema.md](../05-database/auth-schema.md)

- Flyway 只执行结构 Migration。
- 角色/权限引用数据由幂等应用初始化器写入，不放入版本 Migration。
- Rollback SQL 独立保存在 `db/rollback/`，不进入 Flyway 正向路径。
- MySQL Testcontainers 必须验证空库 Migration、外键和唯一约束。

## 7. 错误码

| 错误码 | HTTP | 说明 |
|---|---:|---|
| `AUTH-V-001` | 400 | 参数校验失败 |
| `AUTH-E-001` | 401 | 用户名或密码错误 |
| `AUTH-E-002` | 401 | Token 已过期 |
| `AUTH-E-003` | 401 | Token 无效或已重放 |
| `AUTH-P-001` | 403 | 权限不足 |
| `AUTH-E-004` | 409 | 用户名或邮箱冲突 |
| `AUTH-E-005` | 404 | 用户或角色不存在 |
| `AUTH-E-006` | 409 | 角色名冲突 |
| `AUTH-R-001` | 429 | 请求过于频繁 |
| `AUTH-S-001` | 500 | 内部错误 |

## 8. 测试与门禁

- Unit: JWT、密码边界、业务服务、限流和异常映射。
- Integration: H2 快速 API 测试。
- MySQL Contract: Testcontainers + Flyway + MockMvc，覆盖真实表结构和完整 RBAC 流程。
- Security: 伪造/错误类型 Token、refresh replay、禁用账户、权限提升和限流。
- Benchmark: 真实 MySQL 容器、BCrypt strength=12，登录 P99 < 500ms。
- Coverage: Module instruction coverage >= 80%，Application 启动类除外。

## 9. 兼容性

- 本模块只新增 `/api/v1/auth/**`，不修改已有 `/api/v1/platform/info`。
- 不修改 SDK 或 Plugin SPI。
- Schema 为新增表，正向和回滚脚本均需验证。
- OpenAPI Contract Test 防止实现与文档漂移。
