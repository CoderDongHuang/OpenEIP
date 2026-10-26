# 2026-10-26 依赖与 GitHub 条目纠偏

## 问题与边界

PR #129、#137、#138 的共同失败来自现有 Python 生产锁文件的依赖审计与
Frontend/Website 锁文件的安全扫描。修复保留安全门槛，不新增漏洞忽略、不跳过扫描。
本轮是既有组件的依赖维护，不引入新模块、API、数据库迁移或 SPI；不代表 v0.9 实现完成。

## 修复方案

- 完整保留 Dependabot #137 的七项 Java 更新及 #138 的九项前端更新，建立单一替代 PR。
- 使用 Python 3.12 验证 certifi 2026.10.23、urllib3 2.8.0、Pillow 13.0.0、
  PyJWT 2.15.1，同步生产与开发锁；保留 MCP 1.30.0 和其他未涉及依赖。
  本地 pip-tools 解析已选中这些版本，但下载连接中断，未完成完整重生成；
  因此只定点更新两份既有锁文件的四个版本，不冒充生成成功。安装、pip check、
  审计与 Linux CI 仍为合并条件。
- 升级 Frontend/Website 被安全扫描指出的传递依赖；保留 image-size 本地安全实现。
- Pillow 13、Tinypool 2 涉及主版本变化，必须验证 OCR/文档处理及站点构建，
  不仅检查安装是否成功；若兼容性验证失败继续修复，不以忽略漏洞替代。
- Python 执行现有 lint、格式、类型、单元/契约、OCR benchmark、依赖审计；
  前端执行依赖树、lint、格式、覆盖率及构建；文档站执行类型、安全测试、构建。
  Java、Trivy 仓库/Java runtime/Python image 的最终结论以当前提交 CI 为准。

## GitHub 条目处理

| 条目 | 处理与依据 |
|---|---|
| PR #137 / #138 | 原更新保留到替代修复分支；替代 PR 存在且可审查后，原 PR 才按 superseded 关闭，未合并不等于更新被丢弃 |
| PR #129 | 保留为完整 v0.9 范围纠偏设计包；更新实际讨论入口，不声称实现完成或可发布 |
| Issue #99 | 保持开放：Governance 发布清单仍有 post-tag 扫描、镜像/发布证据及正式 Review 核查项，不能仅凭已发布 alpha 关闭 |
| Issue #123 | 保持开放：Marketplace 的 RFC/ADR 接受、独立审查、迁移/租户回归与质量证据仍待补齐 |
| Discussion #80 / #128 | 保留公开历史；没有公开决定的 RFC 不能因讨论期已过自动标为接受 |
| RFC-0012 | 已创建 [Discussion #139](https://github.com/CoderDongHuang/OpenEIP/discussions/139)，2026-10-26 13:22:18 UTC 开始，最早 2026-11-02 同时刻满一周；尚无接受决定 |

GitHub 的 PR #129 与文档内 RFC-0012 是不同编号体系。RFC-0012 对应的讨论编号为 #139。
Discussions 用于方案讨论与维护者决定；PR 用于审查和合并代码/文档；Issue 用于跟踪验收任务。

## 验证与合并记录

验证进行中。后续补入当前提交的实测及 CI 链接；没有执行的项目不得写为通过。
替代 PR、关闭操作与最终合并由真实 GitHub 状态记录，不登记自动 Approval。
用户负责审批、合并；本轮不发布版本、不移动既有 tag。
