# Scripts

项目辅助脚本——开发、构建、部署用。

## 可用脚本

- `security-scan.ps1`：在 Windows 上创建仅包含 Git 有效文件的临时快照，并运行完整 Trivy 扫描
- `release_smoke.py`：通过 Gateway 验证 v0.3 Auth、File、OCR、Parsing、Knowledge、Embedding、RAG、Chat 和 Agent 整栈链路

```powershell
.\scripts\security-scan.ps1
python scripts\release_smoke.py
```

## 计划内容

- `setup-dev.sh`：一键搭建开发环境
- `start-all.sh`：启动全部服务
- `stop-all.sh`：停止全部服务
- `init-db.sh`：初始化数据库
- `migrate.sh`：数据库迁移
