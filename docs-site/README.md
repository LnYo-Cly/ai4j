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

## AI4J Agent Skills

如果使用支持 Skills 的 agent 工具，可以安装本仓库提供的 Skill，让 AI 更低成本地配合你使用或维护 AI4J。

### 用 AI4J 开发应用

面向 AI4J 使用者，推荐安装 `ai4j-app-builder`。它会帮助 agent 在你的 Java / Spring Boot 项目里选择依赖、配置 provider、编写最小可运行代码，并给出验证命令。

```bash
npx skills add LnYo-Cly/ai4j --skill ai4j-app-builder
```

安装后可以这样调用：

```text
Use $ai4j-app-builder to add AI4J to my Spring Boot app, create a /ai/chat endpoint, and verify it without hardcoding secrets.
```

### 维护 AI4J SDK 仓库

面向本仓库贡献、重构、文档和测试工作，安装 `ai4j-sdk`。它会让 agent 自动了解 AI4J 的模块边界、harness 流程、Java 8 约束和验证命令。

```bash
npx skills add LnYo-Cly/ai4j --skill ai4j-sdk
```

安装后可以这样调用：

```text
Use $ai4j-sdk to help me add a provider feature, choose the right module, and run the smallest useful verification.
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
