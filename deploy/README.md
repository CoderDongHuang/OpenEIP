# Deploy

`v0.2.0-alpha` 的单节点 MVP 容器部署配置。Kubernetes 与 Helm 在对应路线版本实现。

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
`document-files` volume；该本地适配器只用于单节点 MVP，生产部署需替换为受管对象存储。
Python 侧默认使用确定性 Provider 和内存向量库；Kafka、Milvus 与真实模型适配器不在默认
Compose 中启用。

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
