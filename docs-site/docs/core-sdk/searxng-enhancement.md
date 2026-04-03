---
sidebar_position: 40
---

# SearXNG 联网增强（Chat Decorator）

ai4j 提供了一个非常实用的增强模式：把任意 `IChatService` 包装成“先检索再回答”。

核心类：`ChatWithWebSearchEnhance`。

## 1. 设计思路

```text
用户问题
  -> SearXNG 检索
  -> 结果截断/拼接
  -> 注入到最后一条 user message
  -> 调用原始 Chat 服务
```

你不需要改业务控制器，只要替换服务实例。

## 2. 配置

### 2.1 非 Spring

```java
SearXNGConfig searXNGConfig = new SearXNGConfig();
searXNGConfig.setUrl("http://127.0.0.1:8080/search");
searXNGConfig.setEngines("duckduckgo,google,bing");
searXNGConfig.setNums(5);

configuration.setSearXNGConfig(searXNGConfig);
```

### 2.2 Spring

```yaml
ai:
  websearch:
    searxng:
      url: http://127.0.0.1:8080/search
      engines: duckduckgo,google,bing
      nums: 5
```

## 3. 使用方式

```java
IChatService rawChat = aiService.getChatService(PlatformType.OPENAI);
IChatService webEnhanced = aiService.webSearchEnhance(rawChat);

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("请总结今天 AI Agent 领域的重要动态"))
        .build();

ChatCompletionResponse response = webEnhanced.chatCompletion(request);
```

## 4. 流式同样生效

`chatCompletionStream(...)` 会走同样的检索注入逻辑。

## 5. 检索上下文注入策略

当前实现会把检索结果拼接到最后一条用户消息中，包含：

- 网络资料
- 用户问题
- 回答格式要求

如果你要更严格控制，可自定义一个装饰器实现。

## 6. 关键参数建议

- `nums`：3~8（太大会污染上下文）
- `engines`：先从 2~4 个开始
- query 复写策略：必要时先做 query rewrite

## 7. 与知识库检索的边界

- `SearXNG`：公网搜索
- `Pinecone / RAG`：私域知识库检索

两者可以在上层系统中同时使用，但概念上不应混同。

## 8. 安全建议

- 对检索文本做清洗，去除 prompt 注入片段
- 对来源域名做白名单过滤
- 高风险问题加人工复核

## 9. 常见问题

### 9.1 `SearXNG url is not configured`

说明 `SearXNGConfig.url` 为空。

### 9.2 回答质量下降

- 检索结果噪声大
- `nums` 太高
- 注入文本过长

### 9.3 响应慢

- 搜索引擎过多
- SearXNG 服务器性能不足
- 网络链路不稳定
