# Quickstart for Spring Boot

这一页给 Spring Boot 项目一条最短成功路径。

## 1. 最小依赖

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-spring-boot-starter</artifactId>
  <version>2.1.0</version>
</dependency>
```

## 2. 最小配置

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

如果你的网络环境需要代理，再补 `ai.okhttp.proxy-*`。

## 3. 注入 `AiService`

```java
@Autowired
private AiService aiService;

public IChatService chatService() {
    return aiService.getChatService(PlatformType.OPENAI);
}
```

## 4. 首个同步请求

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

## 5. 下一步顺序

推荐继续看：

1. [Spring Boot / 总览](/docs/spring-boot/overview)
2. [Spring Boot / Auto Configuration](/docs/spring-boot/auto-configuration)
3. [First Chat](/docs/start-here/first-chat)
4. [First Tool Call](/docs/start-here/first-tool-call)

如果你已经跑通最小路径，建议直接进入当前主线：

- [Spring Boot / Quickstart](/docs/spring-boot/quickstart)
- [Spring Boot / Configuration Reference](/docs/spring-boot/configuration-reference)
