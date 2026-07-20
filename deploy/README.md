# Deploy

`v0.1.0-alpha` 的本地容器部署配置。Kubernetes 与 Helm 在对应路线版本实现。

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

启动后访问 <http://localhost:3000>。当前 Compose 只包含 Foundation 服务；数据库、Kafka、向量库等将在对应 Spike 验证后加入。

网络受限环境可以只对本次构建设置镜像，不需要修改仓库默认源：

```bash
GRADLE_DISTRIBUTION_URL=https://your-mirror/gradle-8.12.1-bin.zip \
MAVEN_REPOSITORY_URL=https://your-maven-mirror/repository/public \
PIP_INDEX_URL=https://your-pypi-mirror/simple \
docker compose build
```
