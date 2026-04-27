# Choose Your Path

不同读者不该从同一页开始。

这页的作用不是替你讲完所有模块，而是先帮你决定：

- 你应该从哪条主线进入
- 这一条主线会先解释什么
- 读到什么位置时再切到更深的专题树

## 1. 如果你暂时不确定，就走默认主线

最稳的默认顺序是：

1. [Why AI4J](/docs/start-here/why-ai4j)
2. [Architecture at a Glance](/docs/start-here/architecture-at-a-glance)
3. [Quickstart for Java](/docs/start-here/quickstart-java) 或 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
4. [First Chat](/docs/start-here/first-chat)
5. [First Tool Call](/docs/start-here/first-tool-call)
6. [Core SDK / Overview](/docs/core-sdk/overview)

这条线最适合：

- 第一次接 AI4J
- 还没想好最终是停在 SDK、Spring Boot、Agent 还是 Coding Agent
- 想先建立一套完整、稳定的心智模型

## 2. 按目标选入口

### 2.1 我只想先发一个模型请求

从这里开始：

1. [Quickstart for Java](/docs/start-here/quickstart-java)
2. [First Chat](/docs/start-here/first-chat)
3. [Core SDK / Model Access](/docs/core-sdk/model-access/overview)

这条线会先让你确认三件事：

- 依赖是否接对
- provider 配置是否生效
- 第一条 `Chat` 模型调用是否已经跑通

### 2.2 我是 Spring Boot 项目

从这里开始：

1. [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
2. [Spring Boot / 总览](/docs/spring-boot/overview)
3. [Spring Boot / Auto Configuration](/docs/spring-boot/auto-configuration)

这条线优先回答：

- starter 在项目里负责什么
- `AiService` 怎么进入 Spring 容器
- 自动装配、配置前缀和 Bean 扩展的边界是什么

### 2.3 我想搞清 Tool / Function Call / Skill / MCP

从这里开始：

1. [First Tool Call](/docs/start-here/first-tool-call)
2. [Core SDK / Tools](/docs/core-sdk/tools/overview)
3. [Core SDK / Skills](/docs/core-sdk/skills/overview)
4. [Core SDK / MCP](/docs/core-sdk/mcp/overview)

如果你在面试或架构说明里经常把这些概念混掉，这条线应该优先走。

### 2.4 我想接 MCP

从这里开始：

1. [Core SDK / MCP Overview](/docs/core-sdk/mcp/overview)
2. [Positioning and When to Use](/docs/core-sdk/mcp/positioning-and-when-to-use)
3. [Client Integration](/docs/core-sdk/mcp/client-integration)

这条线适合你已经知道：

- 你关心的是协议化外部能力接入
- 不是先做本地函数调用
- 也不是先做通用 Agent runtime

### 2.5 我想做 Agent

从这里开始：

1. [Agent / 总览](/docs/agent/overview)
2. [Agent / Why Agent](/docs/agent/why-agent)
3. [Agent / Quickstart](/docs/agent/quickstart)

你会先看到的是：

- Agent runtime 解决的是什么问题
- 它和 Core SDK 的边界是什么
- runtime、memory、tool loop、orchestration、trace 怎么归位

### 2.6 我想直接用 Coding Agent

从这里开始：

1. [Coding Agent / 总览](/docs/coding-agent/overview)
2. [Coding Agent / 快速开始](/docs/coding-agent/quickstart)
3. [Coding Agent / CLI / TUI](/docs/coding-agent/cli-and-tui)

这条线适合：

- 你已经明确要做本地代码仓交互
- 你主要关心 CLI / TUI / ACP、会话、审批、workspace-aware tools
- 你不是先想学通用 Agent 框架

### 2.7 我想做工作流平台

从这里开始：

1. [Flowgram / 总览](/docs/flowgram/overview)
2. [Flowgram / Why Flowgram](/docs/flowgram/why-flowgram)
3. [Flowgram / 快速开始](/docs/flowgram/quickstart)

这条线优先解释：

- Flowgram 在 AI4J 体系里的位置
- 它和 Agent、Coding Agent 的差别
- 后端 runtime、节点、前后端集成怎么协同

### 2.8 我是为了面试复习或架构复盘

建议按这个顺序读：

1. [Why AI4J](/docs/start-here/why-ai4j)
2. [Architecture at a Glance](/docs/start-here/architecture-at-a-glance)
3. [Core SDK / Overview](/docs/core-sdk/overview)
4. [Core SDK / Strengths and Differentiators](/docs/core-sdk/strengths-and-differentiators)
5. 再按你的重点补 `Spring Boot / Agent / Coding Agent / Flowgram`

这条线最适合：

- 需要先讲清“AI4J 是什么”
- 再讲清“模块怎么分层”
- 最后讲清“为什么这样分层有优势”

## 3. 读文档时的一个简单原则

先读 canonical page，再读深页。

也就是优先读：

- `overview`
- `why`
- `architecture`
- `quickstart`

然后再进入：

- capability page
- API/reference page
- 方案页和案例页

这样不会在一开始就被细节打散。
