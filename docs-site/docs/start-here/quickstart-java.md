# Quickstart for Java

这一页是非 Spring 项目的最短接入路径。

## 1. 最小依赖

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>2.1.0</version>
</dependency>
```

如果你会同时引多个 AI4J 模块，建议直接上 `ai4j-bom`。

## 2. 环境基线

- JDK `1.8+`
- Maven `3.8+`
- 网络可访问目标 provider

## 3. 初始化 `AiService`

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

AiService aiService = new AiService(configuration);
IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
```

## 4. 首个同步请求

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("用一句话介绍 AI4J"))
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
String text = resp.getChoices().get(0).getMessage().getContent().getText();
System.out.println(text);
```

## 5. 下一步顺序

推荐顺序：

1. [First Chat](/docs/start-here/first-chat)
2. [First Tool Call](/docs/start-here/first-tool-call)
3. [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
4. [Core SDK / Model Access](/docs/core-sdk/model-access/overview)

如果你已经跑通最小路径，建议直接进入当前主线：

- [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
- [Core SDK / Model Access / Chat](/docs/core-sdk/model-access/chat)
