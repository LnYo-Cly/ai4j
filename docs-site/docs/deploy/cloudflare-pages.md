---
sidebar_position: 1
---

# Cloudflare Pages 部署

推荐组合：**Docusaurus + Cloudflare Pages + 自定义域名**。

## 1. 为什么选 Cloudflare Pages

- 开源项目可用免费额度
- GitHub 集成后自动构建与自动预览
- 全球 CDN 分发，静态站访问快

## 2. 部署前检查清单

1. `docs-site/docusaurus.config.ts` 中 `url/baseUrl` 正确
2. 本地 `npm run build` 成功
3. 文档链接无 broken links
4. 目标分支策略明确（main/dev）

## 3. Cloudflare Pages 配置

在控制台 `Workers & Pages -> Create -> Pages`：

- Framework preset: `Docusaurus`
- Root directory: `docs-site`
- Build command: `npm run build`
- Build output directory: `build`
- Environment variable: `NODE_VERSION=20`

## 4. 首次部署后验证

- 首页是否可访问
- `/docs/intro` 是否可访问
- 关键专题页是否可访问，例如 `/docs/coding-agent/overview`
- 404 页面是否显示中文

## 5. 自定义域名

建议绑定：`docs.ai4j.dev`

绑定后检查：

- DNS 解析生效
- HTTPS 证书状态正常
- canonical URL 与 sitemap 正确

## 6. 分支策略建议

- `main`：生产文档
- `dev`：预发布文档
- PR 分支：预览环境

## 7. 常见问题

### 7.1 部署后出现 404

优先排查：

1. Root directory 是否误填为仓库根目录
2. `baseUrl` 是否与部署路径一致
3. Cloudflare 缓存是否仍是旧版本

### 7.2 页面还是旧文档

- 触发一次重新部署
- 执行缓存清理（Purge Cache）

### 7.3 本地是中文，线上是英文

- 检查 `i18n.defaultLocale` 是否为 `zh-Hans`
- 检查是否误保留旧的翻译覆盖文件
- 重新构建并部署，避免增量缓存污染

## 8. 持续集成建议

仓库已可配置 docs 构建工作流（例如 `.github/workflows/docs-build.yml`），建议每次 PR 自动执行：

- markdown lint（可选）
- docusaurus build
- broken link 检查
