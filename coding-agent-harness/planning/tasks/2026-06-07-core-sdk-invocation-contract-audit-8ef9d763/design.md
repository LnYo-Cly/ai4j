# Core SDK 调用合同审计设计

## 结论

AI4J 当前应保留并强化真实对象链：

```text
Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse
```

不要新增隐藏式 `ChatClient`、`Ai4j.chat()` 或 `.memory().tools().rag().user().call()` 这类大而全门面，除非先完成单独 API 设计并证明它不会遮蔽现有能力。

## 当前真实合同

### 单实例入口

`Configuration` 是配置聚合对象，持有 `OkHttpClient`、各 provider config、vector config、MCP config、SearXNG config 等。`AiService` 接收 `Configuration`，通过显式 `switch(platform)` 创建具体服务：

- `getChatService(...)`
- `getResponsesService(...)`
- `getEmbeddingService(...)`
- `getImageService(...)`
- `getAudioService(...)`
- `getRealtimeService(...)`
- `getRerankService(...)`
- `getRagService(...)`
- `getIngestionPipeline(...)`
- `getAgentFlow(...)`

这说明 `AiService` 已经是 core SDK 的统一能力工厂，不只是 Chat 的 provider chooser。

### 多实例入口

`AiServiceRegistry` 和 `DefaultAiServiceRegistry` 已经提供正式多实例合同：按 `id` 管理 `AiServiceRegistration`，每个 registration 绑定 `PlatformType` 和 scoped `AiService`。这比新增一个模糊的 provider profile facade 更符合当前代码。

后续如果要升级 provider profile，应优先增强 `AiConfig.platforms` / `AiPlatform` / `AiServiceRegistry`，而不是另起一套 parallel profile 体系。

### Chat 合同

`IChatService` 的合同很窄：

- `chatCompletion(baseUrl, apiKey, ChatCompletion)`
- `chatCompletion(ChatCompletion)`
- `chatCompletionStream(baseUrl, apiKey, ChatCompletion, SseListener)`
- `chatCompletionStream(ChatCompletion, SseListener)`

具体 provider service 负责把 `ChatCompletion` 转成 provider payload，并处理 tool loop、stream、usage、错误等细节。这个层次清楚，不应被首聊 facade 隐藏。

### Tool / MCP

Tool/MCP 不是 client 级 `.tools(new WeatherTools())` 语义。当前真实模型是：

- Java 工具通过注解和 `ToolUtil` 扫描/缓存
- 请求通过 `functions(...)` 和 `mcpServices(...)` 显式白名单暴露工具
- provider chat service 在发送前调用 `ToolUtil.getAllTools(...)`
- 模型返回 tool call 后由 `ToolUtil.invoke(...)` 执行

因此后续升级 Tool 体验时，应围绕“更清晰的工具注册和白名单合同”设计，而不是把工具对象直接塞进 Chat facade。

### RAG

RAG 当前是独立服务：`DefaultRagService` 组合 `Retriever`、`Reranker`、`RagContextAssembler`，输出 `RagResult`、citations、trace。它不是 Chat 请求里的一个简单开关。

因此 `.rag(rag).user(...).call()` 会弱化 RAG 的真实边界。更合理的升级方向是 recipe 或 assembler，让用户显式执行：

1. `ragService.search(query)`
2. 将 `RagResult.context` 放入 `ChatCompletion` 的 system/user 消息
3. 保留 citations / trace

### Memory

`ChatMemory` 是会话事实层，能投影成：

- `toChatMessages()`
- `toResponsesInput()`

这比把 memory 绑定到某个 Chat facade 更通用。后续升级时，应保留它作为 Chat/Responses 共享事实层。

### Spring Boot

Spring starter 已经注入：

- `AiService`
- `AiServiceFactory`
- `AiServiceRegistry`
- `FreeAiService`
- vector stores
- RAG assembler / reranker

Spring 侧要继续强化这些现有 bean，而不是引入新的 `ChatClient` bean 作为默认主线。

## 不应做的事

1. 不要恢复 `ChatClient.openAi(...).chat(...)`。
2. 不要把 `Ai4j.chat().memory().tools().rag().user().call()` 作为主 API。
3. 不要让 docs-site 用一个新 facade 代替真实对象链。
4. 不要新增和 `AiServiceRegistry` 平行的 provider profile 体系。
5. 不要把 RAG 简化成 Chat 的布尔开关。

## 可以做的升级

### 1. 文档级升级

把对象链讲得更顺，而不是隐藏对象链。推荐围绕三个层次写文档：

- Plain Java first chat：真实对象链最小代码
- Service entry and registry：单实例、多实例、Spring Bean 的选择
- Capability composition：Memory、Tool、MCP、RAG 如何显式组合

### 2. 小工具级升级

可以新增不占公共主入口名字的 helper，例如：

- `ChatResponseText.firstText(response)`
- `ChatRequests.user(model, text)`
- `ProviderConfigs.openAi(apiKey)`

这些 helper 只减少重复样板，不改变主调用合同。

### 3. Registry/profile 升级

如果要支持中转平台和多 provider 配置，优先增强：

- `AiPlatform`
- `AiConfig.platforms`
- `DefaultAiServiceRegistry`
- Spring `ai.platforms.*` 绑定

目标是让用户通过 registry 选择 profile：

```java
IChatService chatService = aiServiceRegistry.getChatService("trovebox");
```

而不是新增另一个 `Ai4j.profile("trovebox").chat()` 体系。

### 4. RAG recipe 升级

优先做可复制 recipe，而不是 API facade：

```java
RagResult ragResult = ragService.search(RagQuery.builder()
        .query(question)
        .finalTopK(5)
        .includeTrace(true)
        .build());

ChatCompletion request = ChatCompletion.builder()
        .model(model)
        .message(ChatMessage.withSystem("请基于以下资料回答：\n" + ragResult.getContext()))
        .message(ChatMessage.withUser(question))
        .build();
```

这更符合当前 RAG 合同，也能保留 trace 和 citations。

## 推荐后续任务

1. `docs-site`：重写 first-chat / service-entry / composition 三页，让对象链更好读。
2. `core-sdk`：新增小 helper 设计任务，只允许 helper，不允许新主入口。
3. `spring-starter`：审计 `ai.platforms.*` 配置体验，决定是否增强 `AiServiceRegistry` 文档和绑定。
4. `rag-recipes`：补一组完整对象链 RAG recipe，不改 RAG 核心合同。

