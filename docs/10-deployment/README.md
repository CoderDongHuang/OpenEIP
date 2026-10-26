# 10-deployment（部署设计）

> Docker、Compose、Kubernetes、Helm、Nginx、高可用、扩容。
> 在 Phase 1 搭建脚手架时完成基础内容，后续逐步完善。

## Foundation 状态

- [x] Dockerfile（Java / Python / Frontend）
- [x] Docker Compose Foundation 一键部署
- [ ] Kubernetes Deployment
- [ ] Helm Chart
- [x] Nginx 反向代理配置
- [ ] 高可用架构
- [ ] 扩容策略
- [ ] 备份恢复策略

Kubernetes、Helm、高可用和备份恢复不属于 `v0.1.0-alpha`，在持久化组件通过 Spike 后设计。
## v0.9 完整范围

[Performance HA/扩容/恢复设计](v0.9-performance-ha.md)为 Proposed；最小 Kubernetes 多副本、HPA、数据层 HA、监控和恢复验收纳入 v0.9，尚未交付部署产物或实测。完整 Helm/LTS 安装体验仍属 v1.0；不得用此排除 v0.9 必需 HA 能力。
