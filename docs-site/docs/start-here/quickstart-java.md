# Quickstart for Java

这一页是非 Spring 项目的最短接入路径。

它对应的主模块是：

- `ai4j/`

这条路径的目标很明确：先在一个普通 Java / Maven 项目里，跑通第一条最小模型调用链路。

## 1. 这条路径会先验证什么

如果你按本页完成最小示例，你就能一次性确认：

- 依赖是否已经接对
- provider 配置是否已经生效
- `AiService` 是否已经初始化成功
- `IChatService` 是否能够发出第一条同步请求

它还不会解决的事情包括：

- Spring 容器集成
- Tool / Function Call
- `Responses`
- `MCP`
- Agent runtime

## 2. 最小依赖

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.1.0</version>
</dependency>
```

如果你会同时引多个 AI4J 模块，建议直接上 `ai4j-bom`。

## 3. 环境基线

- JDK `1.8+`
- Maven `3.8+`
- 网络可访问目标 provider

## 4. 初始化 `AiService`

最小思路是：

1. 构造 provider 配置
2. 构造统一 `Configuration`
3. 用 `AiService` 拿到目标能力接口

也就是说，本页真正想让你先建立的心智模型是：

```text
Configuration
    -> AiService
        -> IChatService
```

最小示例如下：

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

AiService aiService = new AiService(configuration);
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
```

## 5. 首个同步请求

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("用一句话介绍 AI4J"))
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
String text = resp.getChoices().get(0).getMessage().getContent().getText();
System.out.println(text);
```

这一步跑通之后，说明你已经验证了最核心的 SDK 主线：

- 模型配置
- 服务获取
- 请求构造
- 响应读取

## 6. 跑通之后应该看什么

如果你的目标是继续理解模型调用主线，推荐顺序是：

1. [First Chat](/docs/start-here/first-chat)
2. [Core SDK / Model Access](/docs/core-sdk/model-access/overview)
3. [Core SDK / Chat](/docs/core-sdk/model-access/chat)
4. [Core SDK / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)

如果你的目标是继续理解整个基座结构，推荐顺序是：

1. [First Chat](/docs/start-here/first-chat)
2. [First Tool Call](/docs/start-here/first-tool-call)
3. [Core SDK / Overview](/docs/core-sdk/overview)
4. [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)

如果这里没跑通，优先回看：

- [Troubleshooting](/docs/start-here/troubleshooting)
- [FAQ](/docs/faq)
