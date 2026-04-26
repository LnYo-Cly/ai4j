---
sidebar_position: 4
---

# Ollama 本地模型接入

历史主题来源：在 Ollama 运行 DeepSeek / Qwen / Llama。

> Legacy note: 本页保留为历史本地模型长文。当前正式入口优先从 [Quickstart for Java](/docs/start-here/quickstart-java)、[Core SDK / Model Access / Chat](/docs/core-sdk/model-access/chat) 和 [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry) 进入。

本页重点：**业务代码尽量不改，只替换模型后端**。

## 1. 基础配置

```yaml
ai:
  ollama:
    base-url: http://127.0.0.1:11434
```

## 2. 获取本地模型服务

```java
IChatService chatService = aiService.getChatService(PlatformType.OLLAMA);
```

## 3. 非流式调用

```java
ChatCompletion req = ChatCompletion.builder()
        .model("qwen2.5:7b")
        .message(ChatMessage.withUser("给我一些 Java 线程池最佳实践"))
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
System.out.println(resp.getChoices().get(0).getMessage().getContent());
```

## 4. 流式调用

```java
chatService.chatCompletionStream(req, new SseListener() {
    @Override
    protected void send() {
        if (!getCurrStr().isEmpty()) {
            System.out.print(getCurrStr());
        }
    }
});
```

## 5. 工具调用兼容性

- 工具声明方式可与 OpenAI 兼容链路保持一致。
- 但不同本地模型在 function-calling 稳定性上差异很大。
- 建议准备“解析失败兜底策略”：
  - 规则解析
  - 重试一次
  - 回退普通回答

## 6. 性能与稳定性建议

- 首次请求会包含模型预热成本，压测要忽略冷启动样本。
- 对大模型建议设置并发上限，避免内存挤兑。
- 流式场景优先传短问题，验证链路后再上复杂任务。

## 7. 与云端模型混用策略

推荐“分层路由”思路：

- 高频低风险任务 -> 本地模型
- 高复杂/高准确任务 -> 云端模型

在 Agent 场景中，可把模型选择逻辑放到路由节点（`StateGraphWorkflow`）。
