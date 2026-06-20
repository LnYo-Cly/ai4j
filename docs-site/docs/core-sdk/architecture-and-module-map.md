# Architecture and Module Map

这一页把文档里的“分层”直接落到仓库真实模块上。

如果你后面要读源码、做架构说明，或者评估模块边界，这一页应该能帮你回答三个问题：

1. 仓库里到底有哪些模块
2. 模块之间的大致依赖方向是什么
3. 我应该先从哪里进入

## 1. 先看真实模块地图

当前根 `pom.xml` 下的 Maven 模块主线是：

```text
ai4j-sdk
├─ ai4j-extension-api
├─ ai4j-plugin-ask-user
├─ ai4j
├─ ai4j-agent
├─ ai4j-coding
├─ ai4j-cli
├─ ai4j-spring-boot-starter
├─ ai4j-flowgram-spring-boot-starter
├─ ai4j-flowgram-demo
└─ ai4j-bom
```

仓库里还可以看到两个重要但性质不同的目录：

- `docs-site/`：文档站源码，不是 Maven 业务模块
- `ai4j-flowgram-webapp-demo/`：前端画布 demo 目录，当前不在根 Maven modules 列表里

## 2. 每个模块分别解决什么

### 2.1 `ai4j-extension-api`

这是插件生态的轻量公共合同模块。

它负责：

- extension manifest
- ServiceLoader discovery
- enable / expose 门禁
- tool、command、Skill、Prompt、Guardrail 的中立 spec
- 插件作者可复用的 validator

### 2.2 `ai4j-plugin-ask-user`

这是官方样板插件模块。

它负责展示一个完整插件包应该怎么组织：

- `Ai4jExtension` 实现
- `META-INF/services` 注册
- `ask_user` tool
- `ask-user` command
- 随 jar 分发的 Skill / Prompt
- validator 和 ServiceLoader 回归测试

它不会打开 UI 或阻塞等待用户输入，只返回宿主可识别的提问请求 envelope。

### 2.3 `ai4j`

这是唯一的 `Core SDK` 模块，也是整个仓库的基础能力底座。

它负责：

- 模型访问
- `Tools`
- `Skills`
- `MCP`
- `ChatMemory`
- RAG 与检索增强
- provider / service / network 扩展

### 2.4 `ai4j-spring-boot-starter`

把 `ai4j` 放进 Spring Boot 容器。

它负责：

- 自动装配
- 配置绑定
- Bean 级扩展

### 2.5 `ai4j-agent`

在 `ai4j` 之上增加通用智能体运行时。

它负责：

- `ReAct`、`CodeAct`、`DeepResearch`
- runtime step loop
- tool registry / executor
- agent memory
- subagent / team / workflow / trace

### 2.6 `ai4j-coding`

在 `ai4j` 和 `ai4j-agent` 之上增加面向代码仓任务的 runtime。

它负责：

- workspace-aware tools
- outer loop
- compact / checkpoint
- session / process / prompt assembly
- coding task 相关策略

### 2.7 `ai4j-cli`

这是 `Coding Agent` 的产品壳层。

它负责：

- CLI
- TUI
- ACP host
- 分发产物和交互入口

### 2.8 `ai4j-flowgram-spring-boot-starter`

这是面向可视化节点工作流平台的后端 starter。

它负责：

- Flowgram runtime 接入
- 内置节点运行支持
- 任务 API
- 与 `Spring Boot`、`Agent` 能力的组合

### 2.9 `ai4j-flowgram-demo`

这是 Flowgram starter 的示例工程，用来展示后端接入和调试路径。

### 2.10 `ai4j-bom`

用于多模块项目的版本对齐，适合团队在引入多个 AI4J 模块时集中管理版本。

## 3. 依赖方向应该怎么理解

从当前模块 `pom.xml` 可以读出一条比较清楚的主线：

```text
ai4j-plugin-ask-user       -> ai4j-extension-api
ai4j-agent                  -> ai4j
ai4j-coding                 -> ai4j + ai4j-agent
ai4j-cli                    -> ai4j + ai4j-coding
ai4j-spring-boot-starter    -> ai4j
ai4j-flowgram-spring-boot-starter
                             -> ai4j-agent + ai4j-spring-boot-starter
ai4j-flowgram-demo          -> ai4j-flowgram-spring-boot-starter
```

这条依赖方向说明了两件事：

1. `ai4j` 是真正的底层核心
2. `ai4j-extension-api` 是插件生态的轻量公共合同，不反向依赖 runtime
3. `Coding Agent` 和 `Flowgram` 都不是凭空出现的，它们是往上叠加出来的运行时或平台层

## 4. 代码定位时应该先去哪

如果你的目标是：

- 看基础模型与能力接入：先看 `ai4j`
- 看插件合同和第三方插件开发：看 `ai4j-extension-api`
- 看官方插件样板：看 `ai4j-plugin-ask-user`
- 看 Spring 自动装配：看 `ai4j-spring-boot-starter`
- 看通用智能体 runtime：看 `ai4j-agent`
- 看本地代码仓任务与持续会话：看 `ai4j-coding`
- 看 CLI / TUI / ACP 产品入口：看 `ai4j-cli`
- 看可视化工作流平台后端：看 `ai4j-flowgram-spring-boot-starter`

这比按目录盲搜更高效，也更符合仓库真实分层。

## 5. 对应到文档阅读顺序

建议按这个顺序对照阅读：

1. [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
2. [Core SDK / Model Access](/docs/core-sdk/model-access/overview)
3. [Core SDK / Tools](/docs/core-sdk/tools/overview)
4. [Core SDK / Skills](/docs/core-sdk/skills/overview)
5. [Core SDK / MCP](/docs/core-sdk/mcp/overview)
6. [Spring Boot / Overview](/docs/spring-boot/overview)
7. [Extension / Plugin Packages](/docs/core-sdk/extension/plugin-packages)
8. [Extension / Ask User Plugin](/docs/core-sdk/extension/ask-user-plugin)
9. [Agent / Overview](/docs/agent/overview)
10. [Coding Agent / Overview](/docs/coding-agent/overview)
11. [Flowgram / Overview](/docs/flowgram/overview)

如果你下一步想继续从代码结构往包结构下钻，建议继续看 [Package Map](/docs/core-sdk/package-map)。

## 6. 还要特别注意两类相邻目录

除了 Maven 主模块，仓库里还有两个阅读时很容易误判角色的目录：

- `docs-site/`：它负责说明体系，不承载生产逻辑
- `ai4j-flowgram-webapp-demo/`：它是前端演示面，用来配合 FlowGram 后端体验，不是 Java 核心运行时的一部分

把这两类目录和 Maven 模块主线分开看，能避免把“文档站结构”或“前端 demo 结构”误认为后端生产分层。
