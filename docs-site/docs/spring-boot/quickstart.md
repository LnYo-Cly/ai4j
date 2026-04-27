# Spring Boot Quickstart

如果你还没有跑通第一个 Spring Boot 请求，先读这一页。

## 1. 最短成功路径

先把目标压缩成四步：

1. 引入 `ai4j-spring-boot-starter`
2. 在 `application.yml` 中配置 `ai.*`
3. 注入 `AiService`
4. 发出第一个 `ChatCompletion`

这一页不追求讲全，只追求最快跑通第一条路径。

## 2. 最小配置

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

如果你的网络环境需要代理，再补 `ai.okhttp.*` 相关配置。

## 3. 最小调用

```java
@Autowired
private AiService aiService;

public String chatOnce(String userInput) throws Exception {
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
    ChatCompletion req = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser(userInput))
            .build();
    return chatService.chatCompletion(req)
            .getChoices().get(0).getMessage().getContent().getText();
}
```

## 4. 跑通之后先看什么

一旦最小请求已经成功，建议按这个顺序继续：

1. [Auto Configuration](/docs/spring-boot/auto-configuration)
2. [Configuration Reference](/docs/spring-boot/configuration-reference)
3. [Bean Extension](/docs/spring-boot/bean-extension)
4. [Common Patterns](/docs/spring-boot/common-patterns)
