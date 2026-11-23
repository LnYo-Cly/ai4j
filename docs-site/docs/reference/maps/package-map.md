---
title: 包地图
description: 用包簇心智模型梳理 ai4j 模块源码分层，指明主能力面与支撑层包的职责，并给出读源码的推荐顺序。
tags: [concept]
---

# 包地图
这一页讲的是 `ai4j/` 模块的包级心智模型。

它的目标不是列出每一个类，而是先回答三个问题：

- `Core SDK` 的代码在源码里大致怎么分层
- 读源码时应该先看哪些包
- 哪些包是主能力面，哪些更像支撑层

## 1. 先记住源码根路径

`Core SDK` 对应模块是：

- `ai4j/`

主要源码根路径是：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/`

所以这一章讨论的所有包，默认都在这个根路径下面。

## 2. 最值得先看的包簇

在 `ai4j` 模块里，最关键的包在 `2.x` 已清晰分层为“DX 入口 -> 协议与传输 -> 中间件与异步流 -> 工具与知识增强”：

- `dx`：`AiClient`、`DefaultAiClient`、`@AiService` JDK 8 动态代理、`@AiTool` / `ToolOut` / `ToolProgress`
- `model` + `protocol`（含 `protocol.gemini`）+ `transport` + `client`：统一多协议消息模型、`CapabilityMatrix`、`GeminiCodec` 与底层传输
- `preset` + `service`（含 `service.factory`、`service.nativeapi`）：`ProviderPresets`、`ProtocolQuirks`、开放式 `Platform` 接口与 `GeminiNativeClient`
- `stream` + `middleware`：Pi 风格 `AiStream<T>`、虚拟线程自适应 `DispatcherProvider` 与 5 大 `ModelMiddleware`
- `codec` + `structured`：`JsonCodec` 统一 Jackson 单栈（取代早期 `fastjson2`）与结构化输出提取
- `platform`：各内置 provider 适配实现（核心 HTTP 栈已摆脱外部厂商重型 SDK 如 `dashscope-sdk-java` 的束缚）
- `tool` + `tools`：本地工具声明、内置工具、`McpToolBridge` SPI 与执行边界
- `skill`：Skill 描述、发现与加载
- `memory`：基础会话上下文能力
- `rag` + `vector` + `rerank` + `websearch` + `document`：基础检索、向量与知识增强抽象（高阶插件集成在独立 `ai4j-rag` / `ai4j-document-tika` 模块）

## 3. 支撑层包大致负责什么

除了主能力面，`ai4j` 里还有一批更偏支撑性质的包：

- `config`：平台配置对象
- `network`：HTTP、连接和底层网络能力
- `auth`：鉴权相关能力
- `interceptor`：HTTP 请求/响应拦截扩展点
- `annotation`：注解式能力暴露
- `listener`：流式或事件监听辅助
- `convert`：对象转换与适配辅助
- `systemone`：`SystemOne` TypeSafe 快速分级决策接口
- `exception`：统一异常定义
- `constant`、`token`：常量与分词/token 相关辅助
- `agentflow`：与外部工作流连接器相关的模型抽象

这些包很重要，但它们通常不是第一次读源码时的第一落点。

## 4. 每个主包簇应该怎么理解

### 4.1 `dx` 与 `service` / `service.factory`

这是 `Core SDK` 的开发者入口层：

- 面向 `2.x` 快速业务开发，先看 `dx` 下的 `AiClient`、`DefaultAiClient`、`AiServiceProxyFactory` 与 `InstanceToolRegistry`；
- 面向传统或底层服务装配，看 `service` 下的 `Configuration`、开放接口 `Platform` / `PlatformType`、`service.nativeapi.GeminiNativeClient` 与 `service.factory` 工厂注册表。

### 4.2 `model`、`protocol`、`preset` 与 `platform`

这是多协议统一编解码与厂商适配层：

- `model` + `protocol` 回答请求/响应如何跨 `CHAT_COMPLETIONS`、`RESPONSES`、`MESSAGES` 与 `GEMINI` 无损映射（含 `thoughtSignature` 保留与跨协议剥离）；
- `preset` 通过 `ProviderPresets` 与 `ProtocolQuirks` 收拢各家 URL、环境变量与微差；
- `platform` 落地具体厂商适配与底层端点调用。

### 4.3 `stream`、`middleware` 与 `codec`

这是非阻塞运行与质量护栏层：

- `stream` 提供 `AiStream` / `DefaultAiStream` / `DispatcherProvider`；
- `middleware` 提供 `RetryMiddleware`、`KeyPoolFailoverMiddleware`、`BudgetGuardMiddleware`、`CognitiveTieringMiddleware` 与 `ShadowEvalMiddleware`；
- `codec` 中的 `JsonCodec` 统一承担全库 Jackson 严格/宽容 JSON 编解码。

### 4.4 `tool` + `tools`

这是本地可调用能力面。

它不只是一个工具列表，而是包含：

- 工具声明
- schema 暴露
- `McpToolBridge` SPI（让 `ai4j` 在零 MCP 依赖下桥接外部 `ai4j-mcp`）
- 执行语义与工具安全边界

### 4.5 `skill`

这是说明资产层。

它的重点不是执行，而是：

- 发现什么 skill
- 何时加载
- 如何把 `SKILL.md` 这一类资源纳入模型上下文

### 4.6 `memory`

这是基座层的基础会话上下文，不是上层 runtime 的完整状态机。

### 4.7 `rag` + `vector` + `rerank` + `websearch` + `document`

这是知识增强主线，高阶 Cordis 扩展与 Tika 文档解析由 `ai4j-rag` 与 `ai4j-document-tika` 独立承载。

如果你要追整条链，通常会看到：

- 入库与切块
- embedding
- vector store
- rerank
- online search
- citations / trace 相关语义

## 5. 读源码的推荐顺序

如果你要读 `ai4j` 核心源码，建议：

1. `dx`（`AiClient` / `AiServiceProxyFactory`）或 `service` / `service.factory`
2. `preset` + `model` + `protocol`（`protocol.gemini`）
3. `stream` + `middleware` + `codec`
4. `platform`
5. `tool` / `tools`（含 `McpToolBridge` SPI）
6. `skill`
7. `memory` 与 `rag` / `vector` / `rerank` / `websearch`

这样能先建立“DX 入口 -> 统一协议与中间件 -> 工具与知识增强”的主线，不容易把 `Core SDK` 和拆出的扩展模块混在一起。

## 6. 已拆出模块与上层模块的边界

在 `2.x` 中，MCP 及更高层能力已经脱离 `ai4j` 核心包，各自拥有独立模块：

- 外部能力与插件扩展：`ai4j-extension-api`（Cordis 微内核）、`ai4j-mcp`（原 `mcp` 包已迁出至此）、`ai4j-rag`、`ai4j-document-tika`、`ai4j-sandbox`、`ai4j-a2a`、`ai4j-plugin-*`
- 通用 Agent 与回放测试：`ai4j-agent`（含 `StructuredOutputSelfHealer`、workflow、team）、`ai4j-testing`
- 长程治理与常驻系统：`ai4j-harness`（`harness` / `harness.fleet`）、`ai4j-ambient`（`ambient.{memory,heartbeat,channel,evolve}`）
- 代码助手与工作流产品壳层：`ai4j-coding`、`ai4j-cli`、`ai4j-web`、`ai4j-spring-boot-starter`、`ai4j-flowgram-spring-boot-starter`
