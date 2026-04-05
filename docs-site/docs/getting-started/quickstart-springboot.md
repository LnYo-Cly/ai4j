---
sidebar_position: 4
---

# Spring Boot 快速接入模式

本页关注的是：如何把 AI4J 直接接进一个 Spring Boot 项目，并完成第一个同步请求、第一个流式接口，以及第一条知识库入库链路。

---

## 1. 依赖

最小方式：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-spring-boot-starter</artifactId>
  <version>2.1.0</version>
</dependency>
```

如果项目里会同时引入多个 AI4J 模块，建议改用 BOM 对齐版本，避免后续升级时多处改版本号。

---

## 2. 配置文件

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
  okhttp:
    proxy-url: 127.0.0.1
    proxy-port: 10809
```

如果你的网络环境不需要代理，可以移除 `ai.okhttp.proxy-*`。

---

## 3. 注入 `AiService`

Starter 接入后的最小入口：

```java
@Autowired
private AiService aiService;

public IChatService chatService() {
    return aiService.getChatService(PlatformType.OPENAI);
}
```

如果这里拿不到 `AiService`，先回头检查依赖、配置前缀和 Spring Boot 版本基线。

如果你已经能注入成功，但想继续搞清楚 starter 到底自动创建了哪些 Bean、属性怎么绑定、为什么 `AiServiceRegistry` 和 `FreeAiService` 也会同时出现，继续看：

- [Spring Boot 自动配置与属性绑定](/docs/getting-started/spring-boot-autoconfiguration)

---

## 4. 先打通同步请求

建议先写一个最小 service 方法：

```java
public String chatOnce(String userInput) throws Exception {
    ChatCompletion req = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser(userInput))
            .build();

    ChatCompletionResponse resp = chatService().chatCompletion(req);
    return resp.getChoices().get(0).getMessage().getContent().getText();
}
```

只要这一步能稳定返回文本，说明依赖、配置、网络和模型基本都已经通了。

---

## 5. 多轮对话：直接用 `ChatMemory`

如果你在 Spring Boot 里提供聊天接口，通常很快就会遇到多轮上下文维护问题。  
这时候不要在 Controller 里手写 `List<ChatMessage>` 累积，直接用 `ChatMemory` 更稳：

```java
public String chatWithMemory(String userInput) throws Exception {
    ChatMemory memory = new InMemoryChatMemory(
            new MessageWindowChatMemoryPolicy(12)
    );
    memory.addSystem("你是一个简洁的企业知识助手");
    memory.addUser(userInput);

    ChatCompletion req = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .messages(memory.toChatMessages())
            .build();

    ChatCompletionResponse resp = chatService().chatCompletion(req);
    String answer = resp.getChoices().get(0).getMessage().getContent().getText();

    memory.addAssistant(answer);
    return answer;
}
```

这适合：

- 一个 HTTP 请求里完成一次多轮调用
- 业务层自己管理 sessionId 和 memory 实例
- 先做基础聊天，再决定是否升级到 Agent runtime

---

## 6. 再打通流式接口

一个最小的 SSE Controller 模板：

```java
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@RequestParam String q) {
    SseEmitter emitter = new SseEmitter(300000L);

    ChatCompletion req = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser(q))
            .build();

    chatService().chatCompletionStream(req, new SseListener() {
        @Override
        protected void send() {
            try {
                emitter.send(getCurrStr());
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    });
    return emitter;
}
```

这一步验证的是：

- 模型流式接口可用
- 服务端能实时把增量转发给前端
- 你的 Web 层没有把输出一次性缓冲到结束才返回

---

## 7. Tool / Function 的最小接法

如果你要在 Spring Boot 服务里接 Tool，最小写法仍然是把函数名传给 `ChatCompletion`：

```java
public String weather() throws Exception {
    ChatCompletion req = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser("查询北京天气并给出建议"))
            .functions("queryWeather")
            .build();

    ChatCompletionResponse resp = chatService().chatCompletion(req);
    return resp.getChoices().get(0).getMessage().getContent().getText();
}
```

更完整的 Function / MCP 工具接法，建议继续看 [多模态与 Function Call 指南](/docs/getting-started/multimodal-and-function-call)。

---

## 8. 最小 RAG 入库

如果你已经在 Spring Boot 项目里启用了某个 `VectorStore`，最小入库方式建议直接写成一个 service：

```java
@Autowired
private AiService aiService;

@Autowired
private VectorStore vectorStore;

public int ingestHandbook(String content) throws Exception {
    IngestionPipeline ingestionPipeline = aiService.getIngestionPipeline(
            PlatformType.OPENAI,
            vectorStore
    );

    IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
            .dataset("kb_docs")
            .embeddingModel("text-embedding-3-small")
            .document(RagDocument.builder()
                    .sourceName("员工手册")
                    .tenant("acme")
                    .biz("hr")
                    .build())
            .source(IngestionSource.text(content))
            .build());

    return ingestResult.getUpsertedCount();
}
```

这里故意没有自动注入一个默认 `IngestionPipeline` Bean，原因是：

- 入库一定依赖 `VectorStore`
- embedding 平台也可能不是全局唯一
- 显式从 `AiService` 取更安全，不会把默认平台语义写死

如果你只想快速验证，也可以直接：

```java
IngestionPipeline ingestionPipeline = aiService.getPineconeIngestionPipeline(PlatformType.OPENAI);
```

---

## 9. 推荐的工程分层

当你把首个请求跑通后，建议尽快按下面方式收敛：

```text
src/main/java
  |- controller      # 协议入口、参数校验、SSE 输出
  |- service         # 业务编排、异常语义、模型调用封装
  |- ai
  |  |- prompts      # system/instruction 模板
  |  |- tools        # Function/MCP 工具封装
  |  `- workflow     # Agent/StateGraph 编排
  `- config          # AI4J 与 HTTP 客户端配置
```

不要把 prompt 拼装、工具逻辑和 Controller 混在一起，否则后续一接 Agent 或 Workflow，就会很难迁移。

---

## 10. 生产化建议

### 10.1 API 层

- 统一鉴权
- 参数长度限制
- 限流与熔断

### 10.2 Service 层

- 将 prompt 组装与业务逻辑解耦
- 为模型错误建立统一错误码映射
- 对长耗时请求配置超时和降级模型

### 10.3 观测层

- 每次请求生成 `requestId`
- 记录模型耗时、tool 次数和失败样本
- 对输出做脱敏后再进日志

---

## 11. 下一步阅读

1. [Chat 与 Responses 实战指南](/docs/getting-started/chat-and-responses-guide)
2. [ChatMemory：基础会话上下文](/docs/ai-basics/chat/chat-memory)
3. [ChatMemory 与 sessionId 管理](/docs/ai-basics/chat/chat-memory-session-management)
4. [Spring Boot 自动配置与属性绑定](/docs/getting-started/spring-boot-autoconfiguration)
5. [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
6. [多模态与 Function Call 指南](/docs/getting-started/multimodal-and-function-call)
7. [常见问题与排障手册](/docs/getting-started/troubleshooting)

