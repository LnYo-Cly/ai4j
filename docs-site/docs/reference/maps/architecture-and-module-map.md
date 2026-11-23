---
title: 架构与模块地图
description: 把 AI4J 仓库的 Maven 模块主线、依赖方向和代码定位入口落到真实工程上，帮助你在读源码或评估模块边界时找到正确落点。
tags: [concept]
---

# 架构与模块地图
这一页把文档里的“分层”直接落到仓库真实模块上。

如果你后面要读源码、做架构说明，或者评估模块边界，这一页应该能帮你回答三个问题：

1. 仓库里到底有哪些模块
2. 模块之间的大致依赖方向是什么
3. 我应该先从哪里进入

## 1. 先看真实模块地图

围绕 `2.x`（`2.8.2-SNAPSHOT`）的微内核模块化演进，仓库 Maven 模块主线按职能划分为五组：

```text
ai4j-sdk
├─ 微内核与协议底座
│  ├─ ai4j-extension-api        # Cordis 微内核契约（ServiceKey、Disposable/作用域、fork、三段门禁）
│  └─ ai4j                      # Core SDK（多协议编解码、AiClient DX、AiStream、Middleware、JsonCodec）
├─ 独立可插拔能力模块（按需引入）
│  ├─ ai4j-mcp                  # MCP 网关、传输与 McpToolBridge SPI 实现
│  ├─ ai4j-rag                  # RAG Extension、QueryPlanner、Judge 与 ContextProjector
│  ├─ ai4j-document-tika        # Apache Tika 富文档解析扩展
│  ├─ ai4j-sandbox              # 远端/隔离沙箱 SPI 与 E2B / Daytona / Cube 等注册管理
│  └─ ai4j-a2a                  # Agent-to-Agent 跨节点协作协议扩展
├─ 运行时、测试、长程治理与常驻系统
│  ├─ ai4j-agent                # 通用 Agent 运行时、Workflow、Team 与 StructuredOutputSelfHealer
│  ├─ ai4j-testing              # ScriptedModelClient / ReplayModelClient 无密钥确定性回放夹具
│  ├─ ai4j-harness              # Harness 2.0 CQRS 事件账本、治理审计、explain 与 5 拓扑 Fleet
│  ├─ ai4j-ambient              # 常驻身份缓存、MemoryProvenanceGate、两段心跳与 5 门控 RSI
│  ├─ ai4j-coding               # Coding Agent workspace 工具、outer loop 与 compaction
│  └─ ai4j-cli                  # CLI / TUI / ACP host 与 Fleet 边缘节点入口
├─ Web、Spring Boot 与可视化工作流
│  ├─ ai4j-web                  # 轻量非阻塞 SSE（AguiSseCodec、OpenAiSseCodec、AiWebStreams）
│  ├─ ai4j-spring-boot-starter  # Spring Boot 2/3/4 单包双栈自动配置、Web SSE 与 /actuator/ai4j
│  ├─ ai4j-flowgram-spring-boot-starter
│  ├─ ai4j-flowgram-demo
│  └─ ai4j-bom                  # 多模块版本对齐 BOM
└─ 官方 Cordis 插件包
   ├─ ai4j-plugin-ask-user      # 宿主介导用户澄清提问样板插件
   ├─ ai4j-plugin-colab         # Google Colab 内核与远程执行沙箱插件
   ├─ ai4j-plugin-codeact       # (Wave 3) CodeAct 脚本执行沙箱插件
   ├─ ai4j-plugin-sandbox-docker # (Wave 3) Docker 容器沙箱提供方插件
   └─ ai4j-plugin-redis         # (Wave 3) Redis 记忆、会话存储与租约通知插件
```

仓库里还可以看到两个重要但性质不同的目录：

- `docs-site/`：文档站源码，不是 Maven 业务模块
- `ai4j-flowgram-webapp-demo/`：前端画布 demo 目录，当前不在根 Maven modules 列表里

## 2. 每个模块分别解决什么

### 2.1 `ai4j-extension-api`

这是插件生态的轻量公共合同与 Cordis 微内核模块。

它负责：

- `ServiceKey<T>` 强类型服务发现与 `ServiceInjector<T>` 反应式绑定
- `Disposable` 与扩展作用域（`ExtensionScope`）LIFO 自动回收
- `ExtensionRegistry.fork()` 子上下文沙箱隔离
- extension manifest、`ServiceLoader` 发现与 `enable / expose` 门禁
- tool、command、Skill、Prompt、Guardrail 的中立 spec 与插件 validator

### 2.2 官方插件矩阵（`ai4j-plugin-*`）

所有官方插件都遵循 `Ai4jExtension` + `META-INF/services` 契约，可按场景独立分发：

- **`ai4j-plugin-ask-user`**：官方样板插件，提供 `ask_user` tool、`ask-user` command 与内嵌 Skill / Prompt（不阻塞挂起线程，只返回宿主可识别提问 envelope）。
- **`ai4j-plugin-colab`**：通过 `ColabExtension` 提供 `colab_execute`、`ColabSandboxProvider` 与 `ColabKernelClient`。
- **Wave 3 新增官方插件**：`ai4j-plugin-codeact`（独立 CodeAct 脚本执行环境）、`ai4j-plugin-sandbox-docker`（Docker 容器沙箱后端）与 `ai4j-plugin-redis`（Redis 对话记忆、Agent 会话存储及 Harness 租约通知），避免主运行时携带不必要的重型传递依赖。

### 2.3 `ai4j`

这是唯一的 `Core SDK` 模块，在 `2.x` 保持轻量单栈：全栈收敛至 Jackson（`JsonCodec`），移除了 `fastjson2`、多余反射扫描及特定厂商重型 SDK（如 `dashscope-sdk-java`）。

它负责：

- 统一多协议接入（`Chat`、`Responses`、`Messages` 与 `Protocol.GEMINI` / `GeminiNativeClient`）与 `thoughtSignature` 跨协议安全剥离
- `ProviderPresets`、`ProtocolQuirks` 与开放式 `Platform` 接口
- `AiClient`、`DefaultAiClient`、`@AiService` JDK 8 代理与 `@AiTool` / `ToolOut` / `ToolProgress`
- Pi 风格非阻塞 `AiStream<T>`、虚拟线程自适应 `DispatcherProvider` 与 5 大 `ModelMiddleware`
- `Tools`、`Skills`、基础 `ChatMemory`、基础检索抽象与 `SystemOne`

### 2.4 独立能力拆分子模块（`ai4j-mcp` / `ai4j-rag` / `ai4j-document-tika` / `ai4j-sandbox` / `ai4j-a2a`）

通过 Cordis 微内核与核心 SPI（如 `ai4j` 中的 `McpToolBridge`）将重型或特定传输集成拆出：

- `ai4j-mcp`：`McpExtension`、MCP client/gateway/server 与 `DefaultMcpToolBridge`
- `ai4j-rag`：`RagExtension`、`ModelClientRagQueryPlanner`、`ModelClientRagJudge`、`RagContextProjector`
- `ai4j-document-tika`：基于 Apache Tika 的多格式文档解析能力
- `ai4j-sandbox`：`SandboxExtension`、`SandboxProviderRegistry` 与多沙箱提供商抽象
- `ai4j-a2a`：`A2AExtension` 与 `a2a_send_task` 跨 Agent 通信能力

### 2.5 `ai4j-web` 与 `ai4j-spring-boot-starter`

- **`ai4j-web`**：提供 `AiWebStreams`、`AguiSseCodec`、`OpenAiSseCodec`、`AiSseEvent` 与 `AiWebStreamOptions`，把 `AiStream` / `ModelStreamListener` 零阻塞桥接为标准 HTTP SSE。
- **`ai4j-spring-boot-starter`**：同时注册 `META-INF/spring.factories` 与 `AutoConfiguration.imports` 且不引用 `javax.annotation` / `javax.servlet`，实现 **Spring Boot 2 / 3 / 4 单包双栈兼容**，并提供 `AiWebAutoConfiguration` 与 `/actuator/ai4j`（`Ai4jActuatorEndpoint`）。

### 2.6 `ai4j-agent` 与 `ai4j-testing`

在 `ai4j` 与 `ai4j-sandbox` 之上提供通用智能体运行时：

- `ReAct`、`DeepResearch`、Workflow、Team orchestration 与 `StructuredOutputSelfHealer`
- `ai4j-testing` 提供 `ScriptedModelClient` 与 `ReplayModelClient` 做无密钥离线确定性回放测试

### 2.7 `ai4j-harness` 与 `ai4j-ambient`

- **`ai4j-harness`**：Harness 2.0 CQRS 事件账本、`WriterEpochFence`、`ContentPinnedConsentRecord`、`SupervisoryAgenda`、因果 `ReadSetEntry`、`WitnessRunner`、可证伪 `DecisionRecord` 与 5 拓扑多节点 Fleet。
- **`ai4j-ambient`**：常驻运行时的身份前缀缓存（`IdentityCorePromptBuilder`）、防投毒隔离（`MemoryProvenanceGate`）、`DreamingConsolidator`、两段式 `AgenticHeartbeat`、`ChannelGateway` 与 5 重发布门控 `PreCommitGoldenGate` RSI。

### 2.8 `ai4j-coding` 与 `ai4j-cli`

在 `ai4j` 和 `ai4j-agent` 之上提供本地代码仓任务 runtime（workspace tools、outer loop、compact / checkpoint），并通过 `ai4j-cli` 暴露 CLI、TUI、ACP host、多协议开关（含 `CliProtocol.GEMINI`）与 Fleet 边缘节点配置（`CliFleetEdgeDescriptor`）。

### 2.9 `ai4j-flowgram-spring-boot-starter` 与 `ai4j-flowgram-demo`

面向可视化节点工作流平台的后端 starter 与调试示例工程（组合 Flowgram runtime、内置节点运行、任务 API 与 trace bridge）。

### 2.10 `ai4j-bom`

管理全仓库多模块 `2.x` 依赖版本对齐，推荐同时引入多个 AI4J artifact 时在 `dependencyManagement` 中导入。

## 3. 依赖方向应该怎么理解

从仓库各模块 `pom.xml` 可以读出清晰的层次与解耦方向：

```text
ai4j-plugin-* / ai4j-sandbox / ai4j-mcp / ai4j-rag / ai4j-a2a
                               -> ai4j-extension-api (+ ai4j / ai4j-agent 按需)
ai4j-web                       -> ai4j (+ spring-webmvc provided/optional)
ai4j-agent                     -> ai4j + ai4j-sandbox
ai4j-testing                   -> ai4j-agent
ai4j-harness                   -> ai4j-agent
ai4j-ambient                   -> ai4j + ai4j-extension-api + ai4j-agent + ai4j-harness + ai4j-testing
ai4j-coding                    -> ai4j + ai4j-agent
ai4j-cli                       -> ai4j + ai4j-coding
ai4j-spring-boot-starter       -> ai4j + ai4j-mcp + ai4j-extension-api (+ optional ai4j-agent / ai4j-web / actuator)
ai4j-flowgram-spring-boot-starter
                               -> ai4j-agent + ai4j-spring-boot-starter
ai4j-flowgram-demo             -> ai4j-flowgram-spring-boot-starter
```

这条依赖方向说明了三件事：

1. `ai4j` 是轻量的协议、DX 与中间件核心，不再直接依赖重型第三方协议/存储 SDK
2. `ai4j-extension-api` 通过 Cordis 微内核契约与 SPI 反向解耦扩展能力，不反向依赖上层 runtime
3. `Coding Agent`、`Harness`、`Ambient` 和 `Flowgram` 都是沿明确边界往上叠加的专项能力层

## 4. 代码定位时应该先去哪

如果你的目标是：

- 看 `AiClient` DX、统一模型协议（含 Gemini）、`AiStream`、中间件与 `JsonCodec`：先看 `ai4j`
- 看 Cordis 微内核合同和插件隔离机制：看 `ai4j-extension-api`
- 看 MCP / RAG / Sandbox / A2A 或官方插件实现：看对应的 `ai4j-mcp` / `ai4j-rag` / `ai4j-sandbox` / `ai4j-a2a` / `ai4j-plugin-*`
- 看非阻塞 HTTP SSE（AG-UI / OpenAI）与 Boot 2/3/4 自动装配、`/actuator/ai4j`：看 `ai4j-web` 与 `ai4j-spring-boot-starter`
- 看通用智能体 runtime 与结构化自愈：看 `ai4j-agent`
- 看长程治理账本与多节点 Fleet：看 `ai4j-harness`
- 看常驻记忆防投毒、心跳与门控自演化：看 `ai4j-ambient`
- 看本地代码仓任务、CLI/TUI/ACP：看 `ai4j-coding` 与 `ai4j-cli`
- 看可视化工作流平台后端：看 `ai4j-flowgram-spring-boot-starter`

这比按目录盲搜更高效，也更符合仓库真实分层。

## 5. 对应到文档阅读顺序

建议按这个顺序对照阅读：

1. [入门 / AiClient DX 与声明式服务](/docs/getting-started/ai-client-dx)
2. [Core SDK / Service Entry and Registry](/docs/capabilities/service-entry)
3. [Core SDK / Model Access](/docs/capabilities/models/overview) -> [Gemini 原生协议](/docs/capabilities/models/gemini) -> [AiStream 与模型中间件](/docs/capabilities/models/ai-stream-and-middleware)
4. [Core SDK / Tools](/docs/capabilities/tools/overview) -> [Skills](/docs/capabilities/skills/overview) -> [MCP](/docs/capabilities/mcp/overview)
5. [Spring Boot / Overview](/docs/integrations/spring-boot/overview) -> [Web SSE 与 Actuator](/docs/integrations/spring-boot/web-sse-and-actuator)
6. [Extension / Cordis 微内核与模块化插件](/docs/extending/plugins/cordis-microkernel) -> [Plugin Packages](/docs/extending/plugins/plugin-packages)
7. [Agent / Overview](/docs/agent/overview) -> [Harness 2.0 治理与 Fleet](/docs/agent/harness-governance-and-fleet) -> [Ambient 与 RSI](/docs/agent/ambient-and-rsi)
8. [Coding Agent / Overview](/docs/products/coding-agent/overview) & [Flowgram / Overview](/docs/products/flowgram/overview)

如果你下一步想继续从代码结构往包结构下钻，建议继续看 [包地图](/docs/reference/maps/package-map)。

## 6. 还要特别注意两类相邻目录

除了 Maven 主模块，仓库里还有两个阅读时很容易误判角色的目录：

- `docs-site/`：它负责说明体系，不承载生产逻辑
- `ai4j-flowgram-webapp-demo/`：它是前端演示面，用来配合 FlowGram 后端体验，不是 Java 核心运行时的一部分

把这两类目录和 Maven 模块主线分开看，能避免把“文档站结构”或“前端 demo 结构”误认为后端生产分层。
