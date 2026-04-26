---
sidebar_position: 3
---

# JDK8 + OpenAI 最小示例（非 Spring）

本页目标：在一个普通 JDK8 项目里，完整打通同步、流式、Tool 和基础知识库入库四条最小链路。

> Legacy note: 本页保留为历史长文示例。当前非 Spring 正式入口优先从 [Quickstart for Java](/docs/start-here/quickstart-java) 进入。

如果你是 Spring Boot 项目，不要从这一页开始，直接看 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)。

---

## 1. 先准备依赖

最小依赖：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.1.0</version>
</dependency>
```

如果你已经在项目中使用 BOM，也可以不单独写版本。

---

## 2. 初始化 `AiService`

这是最小基线模板：

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .addInterceptor(new ErrorInterceptor())
        .connectTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build();

configuration.setOkHttpClient(okHttpClient);

AiService aiService = new AiService(configuration);
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
```

如果你的网络环境需要代理，再给 `OkHttpClient` 增加代理配置。

---

## 3. 同步调用

先把最短路径打通：

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("用一句话介绍 AI4J"))
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
String text = resp.getChoices().get(0).getMessage().getContent().getText();
System.out.println(text);
```

成功标准：

- 没有抛异常
- `resp` 非空
- `text` 非空

---

## 4. 多轮对话：直接用 `ChatMemory`

如果你只是做基础多轮对话，不想每轮都自己维护 `List<ChatMessage>`，可以直接使用 `ChatMemory`：

```java
ChatMemory memory = new InMemoryChatMemory(
        new MessageWindowChatMemoryPolicy(12)
);
memory.addSystem("你是一个简洁的 Java 助手");

memory.addUser("先用一句话介绍 AI4J");

ChatCompletion firstReq = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .build();

ChatCompletionResponse firstResp = chatService.chatCompletion(firstReq);
String firstAnswer = firstResp.getChoices().get(0).getMessage().getContent().getText();
memory.addAssistant(firstAnswer);

memory.addUser("再补一段关于 MCP 的说明");

ChatCompletion secondReq = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .build();
```

这条链路的意义是：

- 不需要自己反复拼接历史消息
- 同一份 memory 后续也能适配 `Responses`
- 如果担心上下文无限增长，可以显式配置 `MessageWindowChatMemoryPolicy`

---

## 5. 流式调用

确认增量输出链路：

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("请分 3 点介绍 AI4J"))
        .build();

SseListener listener = new SseListener() {
    @Override
    protected void send() {
        if (!getCurrStr().isEmpty()) {
            System.out.print(getCurrStr());
        }
    }
};

chatService.chatCompletionStream(req, listener);
System.out.println("\nstream finished");
```

你看到的增量可能是一个字、一个词或一小段文本，这是正常现象，不要把每次回调都理解成单 token。

---

## 6. Tool / Function 调用

最小示例：

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("查询北京天气并给出建议"))
        .functions("queryWeather")
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
System.out.println(resp.getChoices().get(0).getMessage().getContent().getText());
```

这里 `functions("queryWeather")` 是关键，它会触发工具注入与自动工具循环。

对应函数定义示例：

```java
@FunctionCall(name = "queryWeather", description = "查询目标地点的天气预报")
public class QueryWeatherFunction implements Function<QueryWeatherFunction.Request, String> {

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "需要查询天气的目标位置")
        private String location;
    }

    @Override
    public String apply(Request request) {
        return "北京晴，18 到 28 度";
    }
}
```

---

## 7. 最小 RAG 入库

如果你已经准备开始做知识库，不建议再手写：

- `TikaUtil`
- `RecursiveCharacterTextSplitter`
- `Embedding`
- `VectorUpsertRequest`

这一整串装配代码。

更推荐直接走：

```java
VectorStore vectorStore = aiService.getQdrantVectorStore();

IngestionPipeline ingestionPipeline = aiService.getIngestionPipeline(
        PlatformType.OPENAI,
        vectorStore
);

IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .document(RagDocument.builder()
                .sourceName("员工手册")
                .sourcePath("/docs/employee-handbook.md")
                .tenant("acme")
                .biz("hr")
                .version("2026.03")
                .build())
        .source(IngestionSource.text("第一章 假期政策。第二章 报销政策。"))
        .build());

System.out.println(ingestResult.getUpsertedCount());
```

如果你的输入是本地文件，也可以直接：

```java
IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.file(new File("docs/employee-handbook.pdf")))
        .build());
```

这条链路会统一完成：

- 文档装载
- 分块
- metadata 绑定
- embedding
- 向量入库

---

## 8. 建议写成最小测试

你至少应该断言：

- 返回对象非空
- 输出文本非空
- 流式确实出现了中间增量
- Tool 调用链路真的发生了

这样后续换 provider、换模型、换代理配置时，回归会更稳。

---

## 9. 最常见的问题

### 9.1 同步能通，流式没有中间输出

优先检查：

1. 调的是 `chatCompletionStream(...)` 而不是普通接口
2. `send()` 回调里是否实时输出了 `getCurrStr()`
3. 控制台是否有缓冲

### 9.2 Tool 不触发

优先检查：

1. 是否传了 `functions("queryWeather")`
2. 函数名是否与注册名一致
3. 提示词是否明确要求“先调用工具再回答”

### 9.3 一启动就超时或连不上

### 9.4 RAG 入库时报 dataset 或 embeddingModel 缺失

优先检查：

1. `IngestionRequest.dataset(...)` 是否已传
2. `IngestionRequest.embeddingModel(...)` 是否已传
3. `VectorStore` 是否已经正确初始化

优先检查：

- API Key 是否正确
- 网络是否需要代理
- 模型名是否可用

---

## 10. 下一步阅读

1. [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
2. [Core SDK / Model Access](/docs/core-sdk/model-access/overview)
3. [Core SDK / Memory](/docs/core-sdk/memory/overview)
4. [Core SDK / Search & RAG / Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
5. [Core SDK / Model Access / Multimodal](/docs/core-sdk/model-access/multimodal)

