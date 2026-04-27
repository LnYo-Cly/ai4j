# Quickstart for Spring Boot

这一页给 Spring Boot 项目一条最短成功路径。

它对应的主模块是：

- `ai4j-spring-boot-starter/`

这条路径的重点不是“再讲一遍 SDK”，而是先让你确认：

- starter 是否已经进项目
- 配置是否已经被 Spring 正确接收
- `AiService` 是否已经作为 Bean 可用
- 你的应用是否已经能发出第一条模型请求

## 1. 这条路径会先验证什么

跑通本页后，你会先得到一个稳定结论：

- Spring Boot 集成链路已经成立

也就是说，你至少已经确认了：

- 依赖声明
- 自动装配
- 配置绑定
- Bean 注入
- 第一次 `Chat` 请求

## 2. 最小依赖

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-spring-boot-starter</artifactId>
  <version>2.1.0</version>
</dependency>
```

## 3. 最小配置

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

如果你的网络环境需要代理，再补 `ai.okhttp.proxy-*`。

## 4. 注入 `AiService`

这一步的关键是先建立 Spring Boot 视角下的心智模型：

```text
starter + ai.* config
    -> auto-configuration
        -> AiService Bean
            -> IChatService
```

最小示例如下：

```java
@Autowired
private AiService aiService;

public IChatService chatService() {
    return aiService.getChatService(PlatformType.OPENAI);
}
```

## 5. 首个同步请求

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

这一步成功之后，你就已经把 Spring Boot 最重要的第一段链路打通了：

- 配置进入容器
- 服务拿到 Bean
- Bean 能发真实模型请求

## 6. 跑通之后应该看什么

如果你下一步想继续补 Spring Boot 主线，推荐顺序是：

1. [Spring Boot / Overview](/docs/spring-boot/overview)
2. [Spring Boot / Auto Configuration](/docs/spring-boot/auto-configuration)
3. [Spring Boot / Configuration Reference](/docs/spring-boot/configuration-reference)
4. [Spring Boot / Common Patterns](/docs/spring-boot/common-patterns)

如果你下一步想理解底层 SDK 能力，再回到：

1. [First Chat](/docs/start-here/first-chat)
2. [First Tool Call](/docs/start-here/first-tool-call)
3. [Core SDK / Overview](/docs/core-sdk/overview)

如果这里没跑通，优先回看：

- [Troubleshooting](/docs/start-here/troubleshooting)
- [FAQ](/docs/faq)
