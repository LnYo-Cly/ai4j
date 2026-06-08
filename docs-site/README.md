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

## 新用户入口

- 先跑通第一条模型请求：`docs/start-here/five-minute-first-chat.md`
- 普通 Java 细分路径：`docs/start-here/quickstart-java.md`
- Spring Boot 细分路径：`docs/start-here/quickstart-spring-boot.md`
- 能力边界与继续阅读：`docs/start-here/feature-map.md`
- 插件包生态与第三方扩展：`docs/core-sdk/extension/plugin-packages.md`
- 官方 Ask User 插件：`docs/core-sdk/extension/ask-user-plugin.md`
- Spring Boot 插件配置：`ai.extensions.enabled` + `ai.extensions.tools.expose`
- CLI 插件骨架生成：`ai4j-cli extension init <directory> --id <extension-id> --package <java-package>`
- CLI 插件校验：`ai4j-cli extension validate <extension-id>|--all`
- CLI 插件命令执行：`ai4j-cli extension run --enable <extension-id> <command> [arguments...]`
- CLI 插件资源读取：`ai4j-cli extension resource --enable <extension-id> <skill|prompt> <name>`

普通 Java 首聊直接展示
`Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse`
对象链。自定义 HTTP、流式、Tool、MCP、RAG 和 response 细节读取都沿这条主线继续扩展。

## AI4J App Builder Skill

如果使用支持 Skills 的 agent 工具，可以安装本仓库提供的 `ai4j-app-builder` Skill，让 AI 更低成本地配合你在 Java / Spring Boot 项目里使用 AI4J：选择依赖、配置 provider、编写最小可运行代码，并给出验证命令。

```bash
npx skills add LnYo-Cly/ai4j --skill ai4j-app-builder
```

安装后可以这样调用：

```text
Use $ai4j-app-builder to add AI4J first chat to my Java 8 Maven project. Use env vars for secrets, choose the smallest dependency, create a runnable first-chat slice, and give me the verification command.
```

Spring Boot 项目可以这样调用：

```text
Use $ai4j-app-builder to add AI4J to my Spring Boot app, create a /ai/chat endpoint, and verify it without hardcoding secrets.
```

如果要给 AI4J 仓库贡献代码或维护文档，请先阅读仓库根目录的 `AGENTS.md`，并按 `coding-agent-harness/` 的任务、验证和审查流程执行。

## 文档目录结构

- `docs/`
  - `start-here/`：首聊、路径选择、Quickstart、功能地图
  - `core-sdk/`：模型接入、Tool、Skill、MCP、Memory、RAG、扩展和插件包生态
  - `spring-boot/`：starter、配置、自动装配和 Bean 扩展
  - `mcp/`：MCP 能力与治理
  - `agent/`：智能体架构、编排与可观测
  - `coding-agent/`：Coding Agent CLI / TUI / ACP 与本地会话
  - `flowgram/`：FlowGram 后端集成和工作流运行
  - `solutions/`：场景组合路径
  - `deploy/`：文档站部署运维
- `src/pages/`：首页与自定义页面
- `src/theme/NotFound/`：自定义中文 404 页面

## Cloudflare Pages 推荐配置

- Framework preset: `Docusaurus`
- Root directory: `docs-site`
- Build command: `npm run build`
- Build output directory: `build`
- Environment variable: `NODE_VERSION=20`

详细部署步骤见：`docs/deploy/cloudflare-pages.md`
