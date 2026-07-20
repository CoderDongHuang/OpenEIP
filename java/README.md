# Java Platform

Spring Boot 多模块项目——企业业务逻辑、权限管理、Workflow 执行引擎、Connector 管理。

## 技术栈

- Java 21
- Spring Boot 3.x
- Spring Security + OAuth2
- Spring Data JPA (MySQL)
- Spring Kafka
- gRPC (与 Python AI Engine 通信)
- Gradle (Kotlin DSL)

## 模块结构

```
java/
└── platform/
    ├── platform-common/    ← Foundation 共享元数据
    ├── platform-auth/      ← 可启动 Spring Boot 服务壳
    ├── config/checkstyle/
    ├── gradle/wrapper/
    ├── build.gradle.kts
    └── settings.gradle.kts
```

路线模块只在 RFC 和子 SDD 通过后创建。

## 构建与运行

```bash
cd java/platform
./gradlew clean check build
./gradlew :platform-auth:bootRun
```

健康检查：<http://localhost:8080/actuator/health>。

## 编码规范

- Google Java Style Guide
- DDD 分层：api → application → domain → infrastructure
- Checkstyle + Spotless + SpotBugs
- 详见 [docs/00-governance/coding-standard.md](../docs/00-governance/coding-standard.md)
