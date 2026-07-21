# ADR-0005: Auth Token 安全策略

## Status

Accepted

## Date

2026-07-22

## Context

Auth 模块需要为浏览器、Gateway 和后续服务签发可验证凭证。原始草案使用共享 HS256 默认密钥，无法安全区分签发方与验证方，也无法撤销被重放的 refresh token。账户禁用和角色回收也必须尽快生效。

## Decision

- access token 和 refresh token 使用 RS256；生产环境必须注入 Base64 DER 私钥和公钥。
- 仅 `test`/显式开发配置允许生成临时 RSA key pair；生产缺少密钥时启动失败。
- access token 有效期 1 小时，refresh token 有效期 7 天；两者包含 issuer、subject、type、jti、iat、exp。
- JWT 不承载最终授权决定。认证 Filter 依据 subject 查询当前用户、启用状态、角色与权限。
- refresh token 的 `jti` 仅以 SHA-256 哈希保存；刷新时使用数据库行锁单次消费，并签发新的 refresh token。
- 登录、注册和刷新端点在 Auth 服务实施单实例限流；多实例前由 Gateway/Redis 提供分布式限流。

## Consequences

### Positive

- 验证服务只需要公钥，私钥泄漏面小于共享密钥。
- refresh token 重放、禁用账户和过期角色声明可被阻止。
- 没有可用于生产的默认密钥。

### Negative

- 每个受保护请求需要一次用户/权限查询。
- refresh token 由完全无状态变为数据库有状态，需要清理过期记录。
- 开发环境临时 key pair 会在服务重启后使旧 Token 失效。

### Risks

- 单实例限流不能跨节点共享；进入多实例部署前必须迁移到 Gateway + Redis。
- RSA 私钥轮换需要后续引入 `kid` 和多公钥验证窗口。

## Alternatives Considered

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| HS256 | 简单、速度快 | 所有验证方持有签名 Secret；默认值可导致全局伪造 | 拒绝 |
| 无状态 refresh token | 不需要数据库 | 无法可靠撤销和阻止重放 | 拒绝 |
| Redis refresh registry | TTL 与撤销自然 | Foundation 尚无生产 Redis 基线 | 后续候选 |
