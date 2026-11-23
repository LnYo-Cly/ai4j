---
title: Cordis 微内核架构与模块化生态插件
description: 说明 ai4j-extension-api 的 Cordis 微内核原语（ServiceKey、Disposable/EffectScope、ExtensionRegistry.fork()、ServiceInjector）、四大拆分子模块与官方插件矩阵。
tags: [concept, plugin, cordis]
---

# Cordis 微内核架构与模块化生态插件

为了防止核心包 `ai4j` 随着能力增加无限膨胀，同时让第三方和内置插件具备**强类型服务协作、作用域资源自动回收与沙箱子上下文隔离**能力，`2.x` 对 `ai4j-extension-api` 进行了借鉴 Cordis 模型的微内核升级，并将 MCP、RAG、Sandbox 与 A2A 拆分为独立可选模块。

---

## 1. Cordis 微内核四项核心原语

在 `io.github.lnyocly.ai4j.extension` 与 `io.github.lnyocly.ai4j.extension.runtime` 中，所有扩展围绕 `ExtensionContext` 与 `ExtensionRegistry` 运行：

### 1.1 强类型服务键 `ServiceKey<T>`

扩展之间不再靠字符串强转共享对象，而是通过不可变 `ServiceKey<T>` 声明和消费服务：

- 使用 `ServiceKey.of(id, type)` 定义全局唯一服务键（例如 `McpExtension.GATEWAY_SERVICE_KEY`、`RagExtension.RAG_SERVICE_KEY`）；
- 服务提供方调用 `context.provide(key, instance)` 注册实例；
- 消费方调用 `context.findService(key)`（可空）或 `context.service(key)`（缺失抛异常），通过 `key.cast(instance)` 保证编译期类型安全。

### 1.2 作用域托管释放 `Disposable` 与内部 `EffectScope`

每个已启用的扩展在运行时由 `ExtensionRuntimeState` 维护独立的 `ExtensionScope`：

- 扩展可通过 `context.effect(disposable)` 注册任意 `Disposable`（`dispose()` / `close()` / `Disposable.composite(...)`）；
- 注册的工具、命令、拦截器与服务也都会自动纳入该扩展的作用域；
- 当执行 `ExtensionRegistry.disable(id)` 或关闭 `ExtensionRegistry` 时，底层 `CompositeDisposable` 按 **LIFO（后进先出）** 顺序确定性地只释放一次资源（并妥善收集 suppressed 异常），彻底避免线程、连接池或本地进程泄漏。

### 1.3 反应式服务注入 `context.inject(...)` 与 `ServiceInjector<T>`

在真实宿主里，插件加载顺序并不总是固定的。`ExtensionContext` 提供基于 `ServiceKey` 的反应式依赖注入原语：

- `context.inject(ServiceKey<T> key, ServiceInjector<T> injector)`：当目标服务已注册或未来才被另一插件 `provide(...)` 时自动触发 `injector.inject(service)`；
- `ServiceInjector.inject(service)` 可返回一个 `Disposable`，一旦原服务注销或提供方扩展被 `disable`，内核自动清理本次注入产生的副作用并在服务重新上线时重绑；
- 若只需要无返回值回调，也可使用重载 `context.inject(ServiceKey<T>, Consumer<T>)`。

### 1.4 分叉隔离子上下文 `ExtensionRegistry.fork()`

对于自动化验证、Skill 编译试跑或多租户隔离场景，内核提供 `ExtensionRegistry.fork()`：

- 基于父级 `ExtensionRuntimeState` 派生子级运行时状态；
- 子上下文默认继承父上下文已发现的只读扩展与上级服务；
- 在子上下文中新注册的 Tools、Services 或副作用完全隔离，子 `ExtensionRegistry` 关闭时只回收自身作用域，绝不污染宿主全局注册表（典型如 `ai4j-ambient` 中 [`SkillCompiler`](/docs/agent/ambient-and-rsi) 的隔离沙箱校验）。

---

## 2. 从大核心拆出的独立能力模块

过去集中堆在 `ai4j` 或 `ai4j-agent` 的重型集成与外部传输层，现已通过 `Ai4jExtension` + `ServiceKey` + SPI 接口拆为独立按需依赖的 Maven 模块：

| 模块坐标 | 扩展入口（`Ai4jExtension`） | 核心服务键 / 工具 / 职责 | 了解更多 |
| --- | --- | --- | --- |
| `io.github.lnyo-cly:ai4j-mcp` | `McpExtension`（`id = "mcp"`） | 提供 `GATEWAY_SERVICE_KEY` 与 `mcp_call` 工具；通过 `DefaultMcpToolBridge` 实现 `ai4j` 侧 `McpToolBridge` SPI，使核心包零 MCP 依赖；卸载时自动关闭 `McpGateway` | [MCP 总览](/docs/capabilities/mcp/overview) |
| `io.github.lnyo-cly:ai4j-rag` | `RagExtension`（`id = "rag"`） | 提供 `RAG_SERVICE_KEY`、`VECTOR_STORE_KEY`、`CONTEXT_PROJECTOR_KEY` 与 `rag_search` 工具；含 `ModelClientRagQueryPlanner`、`ModelClientRagJudge`、`RagContextProjector`；卸载时自动关闭 `AutoCloseable` 存储 | [RAG 总览](/docs/capabilities/rag/overview) |
| `io.github.lnyo-cly:ai4j-sandbox` | `SandboxExtension`（`id = "sandbox"`） | 提供 `REGISTRY_KEY`、`DEFAULT_PROVIDER_KEY`、`E2B_PROVIDER_KEY`、`DAYTONA_PROVIDER_KEY`、`CUBE_PROVIDER_KEY` 与 `SandboxProviderRegistry`，供 `ai4j-agent` 与插件统一接入远端执行沙箱 | [Sandbox SPI](/docs/agent/governance/sandbox-spi) |
| `io.github.lnyo-cly:ai4j-a2a` | `A2AExtension`（`id = "a2a"`） | 提供 `CLIENT_KEY` 与 `a2a_send_task` 工具，为 `ai4j-agent` 提供 Agent-to-Agent 跨节点任务协作能力 | [A2A 协议](/docs/agent/observability/a2a) |

---

## 3. 官方插件扩展矩阵

基于 [`Ai4jExtension`](/docs/extending/plugins/extension-spi) 与 Cordis 微内核，官方插件按场景独立发布、可通过 `META-INF/services` 自动发现并在显式启用门控后注入宿主运行时：

### 3.1 交互、环境与长驻执行插件

| 插件模块 | 扩展标识与核心能力 | 说明 |
| --- | --- | --- |
| `io.github.lnyo-cly:ai4j-plugin-ask-user` | `AskUserExtension`（`ask-user` / 工具 `ask_user`） | 宿主介导的用户澄清提问、指令与 Skill（见 [Ask-User 官方插件](/docs/extending/plugins/ask-user-plugin)） |
| `io.github.lnyo-cly:ai4j-plugin-colab` | `ColabExtension`（`id = "colab"` / 工具 `colab_execute`） | 提供 `CONFIG_SERVICE_KEY`、`KERNEL_CLIENT_SERVICE_KEY`、`SANDBOX_PROVIDER_SERVICE_KEY`，附带 `ColabSandboxProvider`、`ColabKernelClient`、`colab` Skill 与 `colab-notebook` Prompt |
| `io.github.lnyo-cly:ai4j-ambient` | `AmbientExtension` | 将 `ChannelGateway`、`SupervisoryAgenda`、`MemoryProvenanceGate` 与 `HarnessCommandGateway` 暴露为微内核服务与工具（见 [Ambient 常驻与门控 RSI](/docs/agent/ambient-and-rsi)） |

### 3.2 Wave 3 插件化扩展包（CodeAct / Docker 沙箱 / Redis 存储）

为进一步瘦身 `ai4j-agent` 与 `ai4j-spring-boot-starter` 的第三方运行时依赖，Wave 3 将三类可插拔运行时能力抽离为独立插件模块：

- **`io.github.lnyo-cly:ai4j-plugin-codeact`**：将面向模型生成代码执行的 CodeAct 执行器（GraalJS / Nashorn 沙箱脚本求值）封装为独立按需插件包。
- **`io.github.lnyo-cly:ai4j-plugin-sandbox-docker`**：基于 Docker 容器的本地/隔离代码执行沙箱提供方，通过 `ai4j-sandbox` SPI 与 Cordis 扩展点接入。
- **`io.github.lnyo-cly:ai4j-plugin-redis`**：提供基于 Redis 的分布式对话记忆、Agent 会话存储及 Harness 租约通知扩展，避免强迫未启用 Redis 的应用引入 Redis 客户端传递依赖。

## 下一步建议

- 了解扩展发现、显式启用与暴露门控机制：阅读 [Extension SPI](/docs/extending/plugins/extension-spi) 与 [Plugin Packages](/docs/extending/plugins/plugin-packages)
- 查看子上下文沙箱如何支撑 Skill 编译与自演化安全验证：阅读 [Ambient 常驻、心跳与门控自演化 RSI](/docs/agent/ambient-and-rsi)
