# Core SDK 总览

`Core SDK` 是 AI4J 的唯一基座层，对应仓库里的核心模块是 `ai4j/`。

如果你只能先讲清楚一个部分，那应该先讲清楚这一层。因为后面的 `Spring Boot`、`Agent`、`Coding Agent`、`Flowgram`，本质上都在复用或扩展这里的能力。

## 1. 先用一句话理解这一层

`Core SDK` 解决的是：在 Java 里，如何用一套连续的工程模型把模型调用、工具接入、协议扩展、会话上下文、RAG 和能力扩展组织起来。

所以它不是单独的“Chat 章节”，也不是“工具杂项区”，而是整个 AI4J 的基础能力总装层。

## 2. 这一层到底包含什么

可以把 `Core SDK` 理解成七个并列能力面：

- `Model Access`：`Chat`、`Responses`、流式、多模态、统一请求/返回约定
- `Tools`：本地函数工具、注解式工具、执行模型、安全边界
- `Skills`：可发现、按需加载的说明/模板/工作流资源
- `MCP`：外部能力的协议化接入、网关、传输、发布语义
- `Memory`：基础会话上下文，以及与工具边界的划分
- `Search & RAG`：联网增强、`Embedding`、`Rerank`、向量存储、入库和检索
- `Extension`：provider、模型、服务入口与网络栈扩展

这七块合起来，才构成 AI4J 的基座。

## 3. 代码里这层长什么样

在源码里，`ai4j/src/main/java/io/github/lnyocly/ai4j/` 下面已经能看到这套分层的主干：

- `service`、`platform`
- `tool`、`tools`
- `skill`
- `mcp`
- `memory`
- `rag`、`vector`、`rerank`、`websearch`、`document`
- `network`、`config`、`interceptor`、`auth`

不需要一开始记住所有包名，但你应该先记住：这个模块不是只围绕某一个接口长出来的，而是围绕一整套基础能力面组织的。

## 4. 什么属于 Core SDK，什么不属于

属于这一层的，是“任何上层模块都可能复用的基础能力”。

例如：

- provider 与服务访问
- `Function Call`
- `Skill`
- `MCP`
- `ChatMemory`
- RAG 与检索链
- 扩展点和统一入口

不属于这一层的，是更上层、更场景化的运行时：

- `Spring Boot` 的自动装配与 Bean 扩展
- `Agent` 的 runtime、orchestration、trace
- `Coding Agent` 的 workspace、session、approval、CLI / TUI / ACP
- `Flowgram` 的节点图运行与平台后端接口

这个边界很重要，因为它决定了文档阅读和代码定位都不会混层。

## 5. 为什么这层必须先学

### 5.1 它决定你怎么解释整个项目

如果你能讲清楚 `Core SDK`，你基本就能讲清楚：

- AI4J 不是一个单点 SDK
- 上层模块为什么不是各写各的
- `Function Call`、`Skill`、`MCP` 的归属为什么要分开

### 5.2 它是上层模块共享的能力底座

上层模块复用关系可以简化理解为：

- `Spring Boot` 复用这层的配置和能力装配
- `Agent` 复用这层的模型访问、工具和 `MCP`
- `Coding Agent` 复用这层的工具、`Skill`、`MCP` 和基础模型接入
- `Flowgram` 复用这层的模型、工具、知识库和部分 agentic 能力

所以这一层不是“读过就算”，而是后续所有专题的共同前提。

## 6. 建议怎么读这一层

推荐按下面顺序：

1. [Strengths and Differentiators](/docs/core-sdk/strengths-and-differentiators)
2. [Architecture and Module Map](/docs/core-sdk/architecture-and-module-map)
3. [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
4. [Model Access](/docs/core-sdk/model-access/overview)
5. [Tools](/docs/core-sdk/tools/overview)
6. [Skills](/docs/core-sdk/skills/overview)
7. [MCP](/docs/core-sdk/mcp/overview)
8. [Memory](/docs/core-sdk/memory/overview)
9. [Search & RAG](/docs/core-sdk/search-and-rag/overview)
10. [Extension](/docs/core-sdk/extension/overview)

如果你是为了面试或架构表达，前 4 页就已经是最值得反复复述的主线。
