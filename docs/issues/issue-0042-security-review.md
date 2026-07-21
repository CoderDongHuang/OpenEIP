# Security Review: Issue #42 Auth 模块

> 状态: Passed
> 最后更新: 2026-07-22

## 威胁模型与验证结果

| 威胁 | 控制 | 验证证据 | 结论 |
|---|---|---|---|
| 伪造 JWT | RS256；生产无默认密钥；校验 issuer/type/jti | `JwtServiceTest` 覆盖错误密钥、错误类型、过期、弱密钥和不匹配密钥 | Passed |
| refresh token 重放 | `jti` SHA-256 哈希、悲观行锁、单次消费和原子轮换 | H2 与 MySQL Contract 均验证第二次使用返回 `AUTH-E-003` | Passed |
| 禁用账户继续访问 | 每次 access 认证和 refresh 都加载当前数据库用户状态 | API Integration 覆盖登录、刷新和旧 access token 三条路径 | Passed |
| JWT claim 权限提升 | access token 不携带角色/权限；权限只从数据库读取 | 普通用户访问 RBAC API 返回 403，角色更新即时生效 | Passed |
| 暴力登录/注册/刷新 | 按 `remoteAddr + endpoint` 的有界固定窗口限流 | 限流单测与 429 API Integration，响应含 `Retry-After` | Passed |
| 密码哈希弱化 | BCrypt strength=12；8～72 字符且不超过 72 UTF-8 bytes | `PasswordPolicyTest` 覆盖 ASCII 和多字节边界；基准记录 strength=12 | Passed |
| SQL 注入/约束绕过 | Spring Data 参数绑定；MySQL FK/unique 约束 | MySQL Contract 验证 5 个 FK、唯一约束和非法外键写入 | Passed |
| Secret 泄漏 | 生产配置无默认 RSA key；服务 fail-closed | Trivy 0.72.0 `vuln,secret,misconfig` 仓库扫描无 HIGH/CRITICAL | Passed |
| 依赖漏洞 | Boot JAR 展开后以 `rootfs` 扫描运行时依赖 | Trivy 识别 75 个 `BOOT-INF/lib` JAR，均无 HIGH/CRITICAL | Passed |

## 运行约束

- 本地 Compose 显式允许 ephemeral RSA key，容器重启后 Token 会失效，不得作为生产配置。
- 生产必须从 Secret Manager 注入 PKCS#8 私钥和 X.509 公钥，并保持
  `JWT_ALLOW_EPHEMERAL_KEY=false`。
- 当前限流器只保证单实例边界；进入多实例部署前必须迁移到 Gateway + Redis。
- 后续 RSA key rotation 需要引入 `kid` 和多公钥验证窗口。

## 结论

Issue #42 范围内的认证绕过、Token 重放、权限提升、凭据边界、Secret 和依赖风险均有自动验证证据，未发现阻断级问题。
