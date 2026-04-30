# Core SDK 总览

`Core SDK` 对应仓库里的 `ai4j/` 模块，是 AI4J 整个体系唯一的基础能力层。  
如果你只能先讲清楚一个部分，就应该先讲清楚这一层，因为后面的 `Spring Boot`、`Agent`、`Coding Agent`、`Flowgram` 本质上都在复用或上升它。

## 1. 用一句话定义这一层

`Core SDK` 解决的是：

> 在 Java 里，如何把模型访问、工具暴露、协议扩展、会话上下文、RAG 和服务入口统一收进一套连续的工程模型。

所以它不是单独的 `Chat` SDK，也不是“几个 provider wrapper 的集合”，而是整个 AI4J 的能力总装层。

## 2. 当前这层真正包含什么

从仓库当前代码和文档结构看，`Core SDK` 至少包含这些正式能力面：

- `Model Access`
- `Tools`
- `Skills`
- `MCP`
- `Memory`
- `Search & RAG`
- `Extension`
- 以及被这些能力面共同依赖的 `service entry / registry`

如果只把它理解成模型调用层，你会看漏这层最重要的价值：它是在组织一整套基础能力，而不是单一 API。

## 3. 源码里这层是怎么长出来的

在 `ai4j/src/main/java/io/github/lnyocly/ai4j/` 下，已经能直接看到这套分层骨架：

- `service`、`platform`
- `tool`、`tools`
- `skill`
- `mcp`
- `memory`
- `rag`、`vector`、`websearch`
- `network`、`config`

这说明它不是围绕某一个接口临时扩起来的，而是围绕多条稳定能力面组织的。

## 4. 真实入口链是什么

如果你先按“最常见主线”理解，这层的入口链是：

```text
Configuration
  -> AiService
    -> IChatService / IResponsesService / IEmbeddingService / ...
```

如果进入多实例场景，则会变成：

```text
Configuration + AiConfig.platforms
  -> DefaultAiServiceRegistry
  -> AiServiceRegistry
  -> id -> platformType -> AiService -> I*Service
```

这两条链几乎决定了你后面如何理解 provider、RAG、扩展点和 Spring Boot 装配。

## 5. 这层不是“所有能力都完全对称”

这是非常值得先建立的事实。

从 `AiService` 当前实现看：

- `Chat` 支持平台最广
- `Responses` 支持平台较少
- `Embedding` 只支持 OpenAI/Ollama
- `Audio`、`Realtime` 目前只在 OpenAI 路径存在
- `Rerank` 只支持 Jina/Ollama/Doubao

所以 `Core SDK` 的统一，并不等于“所有 provider 在所有 service 面都完全一致”。  
统一的是入口与抽象，能力覆盖矩阵仍然是显式维护的。

## 6. 什么属于 Core SDK，什么不属于

属于这一层的，是所有上层模块都可能复用的基础能力，例如：

- provider/service 分发
- 统一请求对象与返回对象
- 本地工具暴露
- `Skill`
- `MCP`
- `ChatMemory`
- embedding / rerank / vector / websearch / ingestion

不属于这一层的，是更高一层的运行时或宿主：

- `ai4j-spring-boot-starter` 的自动装配
- `ai4j-agent` 的 runtime、trace、memory orchestration
- `ai4j-coding` 的 workspace、approval、session
- `ai4j-cli` 的宿主界面与交互
- `ai4j-flowgram-*` 的节点图运行与平台集成

这个边界越早建立，后面越不容易混层。

## 7. 为什么这层必须先学

### 它决定你怎么解释整个项目

只要你能讲清楚 `Core SDK`，你基本就能讲清楚：

- AI4J 为什么不是单点模型 SDK
- 为什么 `Function Call`、`Skill`、`MCP` 要分层
- 为什么上层模块不需要各自重新造一套基础能力

### 它是上层模块共享的底座

上层模块和这层的关系可以简化成：

- `Spring Boot` 负责容器化接入
- `Agent` 在这层之上做 runtime
- `Coding Agent` 在这层之上做工作区与流程控制
- `Flowgram` 在这层之上做图式编排与集成

所以这一层不是“读完就忘的基础章节”，而是后续所有专题的共同前提。

## 8. 推荐阅读顺序

建议按下面顺序读：

1. [Strengths and Differentiators](/docs/core-sdk/strengths-and-differentiators)
2. [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
3. [Platform and Service Matrix](/docs/core-sdk/platform-service-matrix)
4. [Model Access](/docs/core-sdk/model-access/overview)
5. [Tools](/docs/core-sdk/tools/overview)
6. [Skills](/docs/core-sdk/skills/overview)
7. [MCP](/docs/core-sdk/mcp/overview)
8. [Memory](/docs/core-sdk/memory/overview)
9. [Search & RAG](/docs/core-sdk/search-and-rag/overview)
10. [Extension](/docs/core-sdk/extension/overview)

## 9. 这一页的结论

> `Core SDK` 在 AI4J 里不是“模型调用那一层”，而是整个工程体系的基础能力层。它统一了入口、抽象和扩展方式，但并不伪装成一个完全对称的多 provider 世界；理解这层的真实支持矩阵和入口链，才是理解整个仓库的前提。
