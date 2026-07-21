# Issue #42: Auth 认证授权模块

> GitHub Issue: [#42](https://github.com/CoderDongHuang/OpenEIP/issues/42)
> Milestone: `v0.2.0 MVP`
> 状态: Ready for Pull Request

## 目标

交付 SAD 已定义的 `platform-auth` 模块，为后续 MVP 模块提供用户注册、登录、RS256 JWT、refresh token 单次轮换、RBAC 和当前用户查询能力。

## 功能验收

- `POST /api/v1/auth/register`: 创建用户并分配 `ROLE_USER`。
- `POST /api/v1/auth/login`: 返回 access token 和 refresh token。
- `POST /api/v1/auth/refresh`: 原子轮换 refresh token，拒绝重放。
- `GET /api/v1/auth/me`: 返回当前用户、角色和权限。
- `GET/POST /api/v1/auth/roles`: 管理员查询和创建角色。
- `PUT /api/v1/auth/users/{id}/roles`: 管理员替换用户角色。
- 被禁用用户不能登录、刷新或继续使用 access token。
- 公开认证端点必须限流。

## 非功能验收

- RS256 密钥必须从外部配置注入；生产配置没有默认密钥和数据库凭据。
- BCrypt strength=12，并拒绝超过 BCrypt 72-byte 边界的密码。
- Unit + Integration 覆盖率不低于 80%。
- MySQL Testcontainers、API Contract、Migration 和 refresh replay 测试通过。
- 登录 Benchmark P99 小于 500ms，并保存机器可读证据。
- Checkstyle、Spotless、SpotBugs 和 HIGH/CRITICAL 安全扫描通过。
- OpenAPI、Sub-SDD、ADR、安全审查、Quality Gate 和 CHANGELOG 同步。

## RFC / ADR 判定

- RFC: 不需要。`platform-auth`、Spring Security、JWT 和 RBAC 已由 SAD 接受，本 Issue 不新增模块边界、SPI 或通信架构。
- ADR: 需要。RS256 密钥管理与 refresh token 轮换/撤销属于安全策略决策，记录于 ADR-0005。
