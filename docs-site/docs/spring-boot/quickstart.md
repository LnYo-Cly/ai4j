# Spring Boot Quickstart

如果你还没有跑通第一个 Spring Boot 请求，先读这一页。

## 1. 这一页要先验证什么

这一页的目标不是讲完整个 starter，而是先确认这条最短成功路径：

1. 引入 `ai4j-spring-boot-starter`
2. 在 `application.yml` 中配置 `ai.*`
3. 注入 `AiService`
4. 发出第一个 `ChatCompletion`

如果这四步已经成立，就说明：

- starter 已经进项目
- 自动装配已经生效
- 配置绑定已经成立
- 容器内已经能拿到可用 AI4J Bean

## 2. 最小依赖

最小依赖就是：

- `ai4j-spring-boot-starter`

这条路径默认建立在 `Core SDK` 已被 starter 带入容器的前提上，不需要你手动再拼一遍最底层入口。

## 3. 最小配置

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
```

如果你的网络环境需要代理，再补 `ai.okhttp.*` 相关配置。

这一步最核心的判断标准不是“字段背没背全”，而是：

- 你的配置是否真的进入 Spring 环境
- provider 的最小必需字段是否已经具备

## 4. 最小调用

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

这一段代码真正验证的是：

- Spring 已经注入 `AiService`
- `AiService` 能按 `PlatformType` 给你取到服务
- 第一条模型请求已经能在业务 Bean 中成功跑通

## 5. 跑通之后不要立刻跳得太深

跑通第一条请求之后，建议按这个顺序继续：

1. [Auto Configuration](/docs/spring-boot/auto-configuration)
2. [Configuration Reference](/docs/spring-boot/configuration-reference)
3. [Bean Extension](/docs/spring-boot/bean-extension)
4. [Common Patterns](/docs/spring-boot/common-patterns)

如果你还不清楚底层能力边界，再回去补：

1. [Core SDK / Overview](/docs/core-sdk/overview)
2. [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
3. [Core SDK / Model Access](/docs/core-sdk/model-access/overview)

## 6. 没跑通时优先排什么

优先检查：

1. starter 是否真的引入
2. `ai.*` 配置是否被 Spring 读取
3. API Key / 网络 / 代理是否正常
4. 你拿不到 Bean，还是 Bean 能拿到但请求失败

如果是入口阶段问题，先回看：

- [Start Here / Troubleshooting](/docs/start-here/troubleshooting)
- [Configuration Reference](/docs/spring-boot/configuration-reference)
