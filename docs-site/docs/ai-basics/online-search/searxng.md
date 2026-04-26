---
sidebar_position: 2
---

# SearXNG 联网增强

AI4J 在联网增强上的官方入口，是把现有 `IChatService` 包装成“先搜索，再回答”的装饰器链路。

核心入口：

- `AiService.webSearchEnhance(IChatService chatService)`
- `ChatWithWebSearchEnhance`
- `SearXNGConfig`

---

## 1. 适用场景

适合：

- 问题依赖实时网页信息；
- 你使用的模型没有原生联网；
- 你希望搜索源和搜索引擎自己可控；
- 你想保持原业务代码仍然面向 `IChatService`。

---

## 2. 工作方式

```text
用户问题
  -> ChatWithWebSearchEnhance
  -> 请求 SearXNG
  -> 结果截断 / 过滤 / 拼接
  -> 注入最后一条用户消息
  -> 调用原始 IChatService
```

这意味着：

- 业务控制器不需要改调用模型的主接口；
- 你是在 Chat 链路上做增强，而不是重写一套 Agent。

---

## 3. 基础配置

### 3.1 非 Spring

```java
SearXNGConfig searXNGConfig = new SearXNGConfig();
searXNGConfig.setUrl("http://127.0.0.1:8080/search");
searXNGConfig.setEngines("duckduckgo,google,bing");
searXNGConfig.setNums(5);

Configuration configuration = new Configuration();
configuration.setSearXNGConfig(searXNGConfig);
configuration.setOpenAiConfig(openAiConfig);

AiService aiService = new AiService(configuration);
```

### 3.2 Spring Boot

```yaml
ai:
  websearch:
    searxng:
      url: http://127.0.0.1:8080/search
      engines: duckduckgo,google,bing
      nums: 5
```

---

## 4. 使用方式

```java
IChatService rawChat = aiService.getChatService(PlatformType.OPENAI);
IChatService webEnhancedChat = aiService.webSearchEnhance(rawChat);

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("请总结今天 AI Agent 领域的重要动态"))
        .build();

ChatCompletionResponse response = webEnhancedChat.chatCompletion(request);
```

---

## 5. 流式是否同样生效

可以。

`chatCompletionStream(...)` 仍会先做搜索增强，再把增量输出回传给你的流式监听器。

---

## 6. 参数建议

- `url`：必填，指向 SearXNG `/search`
- `engines`：建议先从 2~4 个开始
- `nums`：建议 3~8，过大容易把上下文污染得很严重

工程上还建议增加：

- query rewrite
- 来源域名过滤
- 注入文本长度上限

---

## 7. 安全与可观测

联网增强比普通聊天多了外部输入风险，至少要补这几层：

- 对搜索结果做长度截断与去重；
- 对网页文本做 prompt 注入清洗；
- 对来源域名做白名单或分级；
- 记录 query、engine、命中域名、注入长度和最终回答。

如果不记录这些字段，后面很难排查“为什么这次回答不可信”。

---

## 8. 什么时候不要用联网增强

- 问题主要依赖内部知识库；
- 场景禁止访问公网；
- 强 SLA 接口对延迟非常敏感；
- 你已经有稳定的私域检索链路。

这种情况下，优先看 [RAG / 知识库增强总览](/docs/ai-basics/rag/overview)。

---

## 9. 常见问题

### 9.1 `SearXNG url is not configured`

说明 `SearXNGConfig.url` 为空或未加载成功。

### 9.2 检索成功但回答质量差

- `nums` 太大；
- `engines` 太杂；
- 注入上下文太长；
- 提示词没有限制“基于搜索结果回答”。

### 9.3 响应慢

- 搜索引擎过多；
- SearXNG 服务本身慢；
- 外网链路不稳定。

---

## 10. 继续阅读

1. [联网增强总览](/docs/ai-basics/online-search/overview)
2. [RAG / 知识库增强总览](/docs/ai-basics/rag/overview)

