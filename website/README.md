# Website

OpenEIP 官方文档站点——基于 Docusaurus。

## 技术栈

- Docusaurus 3.x
- Markdown + MDX
- 中文文档（英文版在首个稳定版本前补齐）
- 全文搜索 (Algolia / 本地搜索)

## 本地开发

```bash
cd website
npm install
npm start
```

## 构建

```bash
npm run build
```

## 页面结构

```
website/
├── docs/
│   └── intro.md
├── src/
│   ├── pages/          ← 首页等自定义页面
│   └── css/
├── docusaurus.config.ts
├── sidebars.ts
├── package.json
└── tsconfig.json
```

## 部署

GitHub Actions 会验证网站构建。正式发布后再启用 Pages/Vercel 部署：

```
GitHub Push → Actions → Build → Deploy to GitHub Pages / Vercel
```
