# Deploy

当前 Compose 是单节点开发部署配置，不是 HA 拓扑。v0.9 最小 Kubernetes/HA/HPA 方案见[部署设计](../docs/10-deployment/v0.9-performance-ha.md)（Proposed，部署产物待实现）；完整 Helm 属后续 LTS。

## 目录结构

```
deploy/
├── docker/
│   ├── Dockerfile.java
│   ├── Dockerfile.python
│   └── Dockerfile.frontend
├── docker-compose/
│   ├── docker-compose.yml
└── nginx/
    └── nginx.conf
```

## 启动

在仓库根目录执行：

```bash
docker compose up --build
```

启动后访问 <http://localhost:3000>。Compose 会同时启动 Java 控制面所需的 MySQL，并由
`platform-app` 聚合 Auth、Document、Knowledge、Chat 与 Agent Java 模块。上传的原始文件保存在
`document-files` volume；该本地卷仅支持单节点开发；v0.9 拟通过 HA RWX 共享存储保持现有 ObjectStorage 契约，受管对象存储另行评估。
Python 开发环境默认使用确定性 Provider，但默认 Compose 已启用 Milvus 持久向量检索、Elasticsearch 全文检索及其 etcd/MinIO 依赖；它们均为开发单节点配置。真实模型需要独立凭据与配置，不能用确定性结果推断质量。

该 Compose 仅用于本地开发。MySQL 使用本地凭据，并显式设置
`JWT_ALLOW_EPHEMERAL_KEY=true`，因此 Java 容器重启后已有 Token 会失效。

生产部署必须通过 Secret Manager 注入 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、
`JWT_PRIVATE_KEY_BASE64` 和 `JWT_PUBLIC_KEY_BASE64`，并保持
`JWT_ALLOW_EPHEMERAL_KEY=false`。RSA 密钥缺失、强度不足或不匹配时服务会拒绝启动。

网络受限环境可以只对本次构建设置镜像，不需要修改仓库默认源：

```bash
GRADLE_DISTRIBUTION_URL=https://your-mirror/gradle-8.12.1-bin.zip \
MAVEN_REPOSITORY_URL=https://your-maven-mirror/repository/public \
PIP_INDEX_URL=https://your-pypi-mirror/simple \
docker compose build
```
