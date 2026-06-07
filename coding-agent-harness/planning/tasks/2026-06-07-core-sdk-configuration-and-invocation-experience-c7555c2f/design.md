# Core SDK 配置与调用体验升级设计

## 结论

AI4J 下一轮体验升级不应从新增 `ChatClient` 或 `Ai4j.chat()` 开始，而应沿着现有真实主线做减法：

`Configuration -> AiService -> IChatService -> ChatCompletion -> ChatCompletionResponse`

真正值得升级的是配置创建、profile/registry、OpenAI-compatible 中转平台接入、Spring Boot 配置示例和组合 recipe。这样能降低用户接入成本，同时不遮蔽 Tool/MCP/RAG/Memory/Responses 等能力边界。

## 当前事实

### Plain Java

当前 Plain Java 可以直接工作，但样板偏多：

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

AiService aiService = new AiService(configuration);
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
```

这条链真实、完整、可扩展，但对小白用户来说第一屏动作太多。

### Spring Boot

Spring Boot starter 已经暴露 `AiService`、`AiServiceRegistry`、`FreeAiService`，并支持：

- `ai.openai.api-key`
- `ai.openai.api-host`
- `ai.platforms[*].id`
- `ai.platforms[*].platform`
- `ai.platforms[*].api-host`
- `ai.platforms[*].api-key`

这说明多 provider / 多 profile 的正式抽象已经存在，不需要另起一套 profile facade。

### OpenAI-compatible / 中转平台

`OpenAiConfig` 和 `AiPlatform` 都已有 `apiHost`、`apiKey`、`chatCompletionUrl`、`embeddingUrl` 等字段。TroveBox 这类中转平台可以作为 `platform: openai` + 自定义 `api-host` 进入体系。

缺口不是能力不存在，而是：

1. 命名上用户更熟悉 `baseUrl`，当前字段叫 `apiHost`。
2. docs-site 需要给出中转平台配置模板。
3. registry 层需要明确“profile id 是业务路由名，不是 provider 名”。

## 升级原则

1. 保留对象链主合同。
2. 只在配置和 recipe 层降低样板，不新增隐藏式能力门面。
3. Provider/profile 统一走 `AiServiceRegistry`。
4. Tool/MCP 仍由请求级 `functions(...)` / `mcpServices(...)` 控制暴露面。
5. RAG 仍是 `RagService` 和 context assembly，不塞进 Chat facade。
6. Memory 仍是可投影到 Chat/Responses 的事实层。

## 建议升级

### P0：文档与配置 recipe，优先做

这些不需要改 Java API：

1. docs-site 增加“Plain Java 最小对象链”页面。
2. docs-site 增加“Spring Boot 多 provider/profile”页面。
3. docs-site 增加“OpenAI-compatible / 中转平台配置”页面，示例覆盖 TroveBox。
4. README 把赞助商 TroveBox 和 OpenAI-compatible 配置入口连起来。
5. 给 RAG、Tool/MCP、Memory 写组合 recipe：先显式拿 service，再组装 request。

### P1：轻量配置 helpers，需要 API 评审但风险可控

这些可以单独开实现任务评审：

1. `Configuration` 静态工厂：

```java
Configuration configuration = Configuration.openAi(apiKey);
```

或：

```java
Configuration configuration = Configuration.openAi(apiKey, apiHost);
```

这不是 Chat facade，只是少写配置样板，仍返回真实 `Configuration`。

2. `AiConfig` / `AiPlatform` builder：

```java
AiConfig aiConfig = AiConfig.builder()
        .platform("default", "openai")
        .apiKey(apiKey)
        .apiHost(apiHost)
        .build();
```

3. `AiServiceRegistry` 便利方法：

```java
IChatService chat = registry.chat("default");
```

是否采用短名要单独评审；现有 `getChatService(id)` 已经清楚，短名只是锦上添花。

### P2：Starter 体验增强，需要结合配置绑定实现

1. `ai.default-platform-id`：让业务可以声明默认 profile。
2. `AiServiceRegistry#getDefaultChatService()`：只有存在默认 id 时才暴露。
3. 配置校验：`id`、`platform`、`api-key`、`api-host` 缺失时给更清楚的错误。
4. OpenAI-compatible alias：评审是否增加 `base-url` 到 `api-host` 的别名绑定。

这些要注意 Java 8 和 Spring Boot 版本兼容。

## 明确不做

1. 不恢复 `ChatClient.openAi(...).chat(...)`。
2. 不新增 `Ai4j.chat().memory().tools().rag().user().call()`。
3. 不把 Tool/MCP 注册变成隐式全局暴露。
4. 不把 RAG 变成 Chat 请求上的一个隐藏开关。
5. 不把 Memory 绑定死到 Chat，避免影响 Responses。

## 推荐实现波次

### Wave 1：docs-site 真实体验重写

- Plain Java 最小对象链
- Spring Boot 最小 chat
- 多 provider/profile
- OpenAI-compatible / TroveBox 中转平台
- Tool/MCP/RAG/Memory 组合 recipe

### Wave 2：配置 helper API

- `Configuration.openAi(...)`
- `Configuration.openAiCompatible(...)` 或同等命名
- 本地单元测试覆盖默认 URL、custom host、OkHttp 保留

### Wave 3：Registry / Starter 默认 profile

- `ai.default-platform-id`
- registry 默认 profile 查询
- 配置校验和错误消息

## 下一步建议

先执行 Wave 1。原因是它不改变 API，却能立刻把项目表达从“功能堆叠”变成“可复制接入路径”。等 docs-site 把真实主线讲清楚后，再决定 Wave 2 的 helper API 是否值得做。
