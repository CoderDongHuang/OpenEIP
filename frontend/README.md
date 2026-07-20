# Frontend

React + Vite + TypeScript——OpenEIP 前端管理界面。

## 技术栈

- React 18
- TypeScript
- Vite
- Ant Design
- Zustand (状态管理)
- Workflow Canvas 和 SSE 将在对应版本引入

## 项目结构

```
frontend/
├── src/
│   ├── App.tsx
│   └── main.tsx
├── package.json
├── package-lock.json
├── tsconfig.json
├── vite.config.ts
├── eslint.config.js
└── .prettierrc.json
```

功能模块将在对应子 SDD 通过后增加目录，避免提前创建空架构。

## 编码规范

- ESLint + Prettier
- React Hooks 优先
- 组件：PascalCase
- 函数/变量：camelCase
- 详见 [docs/00-governance/coding-standard.md](../docs/00-governance/coding-standard.md)
