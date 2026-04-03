# docs-site

AI4J 官方 Docusaurus 文档站（中文主站）。

## 本地开发

```bash
cd docs-site
npm install
npm start
```

## 构建与预览

```bash
npm run clear
npm run build
npm run serve
```

## 文档目录结构

- `docs/`
  - `getting-started/`：接入与排障
  - `guides/`：场景实践
  - `mcp/`：MCP 能力与治理
  - `agent/`：智能体架构、编排与可观测
  - `deploy/`：文档站部署运维
- `blog/`：发布动态与演进记录
- `src/pages/`：首页与自定义页面
- `src/theme/NotFound/`：自定义中文 404 页面

## Cloudflare Pages 推荐配置

- Framework preset: `Docusaurus`
- Root directory: `docs-site`
- Build command: `npm run build`
- Build output directory: `build`
- Environment variable: `NODE_VERSION=20`

详细部署步骤见：`docs/deploy/cloudflare-pages.md`
