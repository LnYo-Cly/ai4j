---
sidebar_position: 2
---

# SearXNG 联网搜索增强

这套能力适合：模型本身不带联网，或你需要更可控的“检索 -> 回答”链路。

在 ai4j 中，核心入口是：

- `AiService.webSearchEnhance(IChatService chatService)`
- 装饰器实现类：`ChatWithWebSearchEnhance`
- 配置类：`SearXNGConfig`

## 1. 机制概览

`ChatWithWebSearchEnhance` 会在调用模型前做三件事：

1. 读取用户最新问题
2. 请求 SearXNG（JSON）获取检索结果
3. 将检索结果拼接进 prompt，再交给原始 `IChatService`

所以你的业务层仍调用同一个 `IChatService` 接口。

## 2. 基础配置

### 2.1 非 Spring

```java
SearXNGConfig searXNGConfig = new SearXNGConfig();
searXNGConfig.setUrl("http://127.0.0.1:8080/search");
searXNGConfig.setEngines("duckduckgo,google,bing");
searXNGConfig.setNums(5);

Configuration configuration = new Configuration();
configuration.setSearXNGConfig(searXNGConfig);
configuration.setOpenAiConfig(openAiConfig);
configuration.setOkHttpClient(okHttpClient);
```

### 2.2 Spring Boot

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
AiService aiService = new AiService(configuration);
IChatService rawChat = aiService.getChatService(PlatformType.OPENAI);
IChatService webEnhancedChat = aiService.webSearchEnhance(rawChat);

ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("2026 年最新 AI Agent 框架趋势是什么？"))
        .build();

ChatCompletionResponse response = webEnhancedChat.chatCompletion(request);
System.out.println(response.getChoices().get(0).getMessage().getContent().getText());
```

## 4. 流式也可以直接用

```java
webEnhancedChat.chatCompletionStream(request, new SseListener() {
    @Override
    protected void send() {
        if (!getCurrStr().isEmpty()) {
            System.out.print(getCurrStr());
        }
    }
});
```

## 5. 与 RAG 的关系

- SearXNG：公网检索，时效强
- Pinecone/RAG：私域检索，准确强

推荐混合策略：

1. 先查私域（RAG）
2. 证据不足时再查 SearXNG
3. 合并证据后再生成

## 6. `SearXNGConfig` 参数建议

- `url`：必填，SearXNG `/search` 地址
- `engines`：建议先从 2~4 个引擎开始
- `nums`：建议 3~8，过大容易污染上下文

## 7. 安全建议

- 配置域名白名单（至少在网关层做限制）
- 对检索结果做长度截断与去重
- 对外部文本做 prompt 注入防护（去系统指令片段）

## 8. 可观测建议

最少记录以下字段：

- `query`
- `engines`
- 命中来源域名列表
- 注入模型前上下文长度
- 最终回答与引用

这样才能排查“为什么这次回答不可信”。

## 9. 常见问题

### 9.1 报错 `SearXNG url is not configured`

- `SearXNGConfig.url` 未配置或为空。

### 9.2 检索成功但回答质量差

- `nums` 太大导致噪声过多
- `engines` 配置太杂
- 提示词没有限制“基于检索结果回答”

### 9.3 联网响应太慢

- 减少 `engines` 数量
- 减少 `nums`
- 给 SearXNG 独立超时设置或本地部署

## 10. 何时不建议开启联网增强

- 高实时低延迟接口（如强 SLA 的在线问答）
- 强隐私场景且不允许公网访问
- 你已有稳定内部检索链路（RAG 足够）

这时建议把 SearXNG 做成“可选降级路径”，按场景开启。
