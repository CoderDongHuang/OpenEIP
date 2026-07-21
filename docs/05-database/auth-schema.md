# Auth Database Design

> 版本: 1.0 | Issue: [#42](https://github.com/CoderDongHuang/OpenEIP/issues/42)

## 所有权

`platform-auth` 独占以下表，其他模块只能通过 Auth API/Token 获取身份信息，不允许直接写表。

| 表 | 用途 |
|---|---|
| `auth_users` | 用户凭据、启用与软删除状态 |
| `auth_roles` | RBAC 角色 |
| `auth_permissions` | 权限码 |
| `auth_user_roles` | 用户与角色关系 |
| `auth_role_permissions` | 角色与权限关系 |
| `auth_refresh_tokens` | refresh jti 哈希、过期、使用和撤销状态 |

## 关键约束

- username/email/role name/permission code/token hash 唯一。
- 所有关联表使用数据库外键；删除用户级联删除 refresh token 和用户角色。
- 删除角色或权限前必须先解除引用，避免静默丢失授权关系。
- `auth_refresh_tokens.token_hash` 保存 64 字符 SHA-256 hex，不保存 JWT。
- `expires_at` 和 `user_id` 建索引，用于清理和用户撤销。

## Migration

- 正向: `db/migration/V2.0.0__init_auth_schema.sql`
- 回滚: `db/rollback/U2.0.0__init_auth_schema.sql`
- 引用数据: Java `AuthReferenceDataInitializer` 幂等初始化。

Migration 兼容性由 MySQL Testcontainers 从空库执行，并检查全部表、外键和索引。进入已有环境前仍需备份并演练 rollback。
