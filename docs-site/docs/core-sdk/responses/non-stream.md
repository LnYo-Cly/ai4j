---
sidebar_position: 20
---

# Responses（非流式）

`Responses API` 适合事件化语义更强的场景。本页先讲非流式。

## 1. 核心对象

- 服务接口：`IResponsesService`
- 请求：`ResponseRequest`
- 响应：`Response`

## 2. 支持平台

当前 `AiService#getResponsesService(...)` 支持：

- `OPENAI`
- `DOUBAO`
- `DASHSCOPE`

## 3. 最小示例

```java
IResponsesService responsesService = aiService.getResponsesService(PlatformType.DOUBAO);

ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input("请用一句话介绍 Responses API")
        .instructions("用中文输出")
        .maxOutputTokens(256)
        .build();

Response response = responsesService.create(request);
System.out.println(response);
```

## 4. 常用字段

`ResponseRequest` 常用参数：

- `model`
- `input`（可字符串，也可结构化对象）
- `instructions`
- `reasoning`
- `tools`
- `toolChoice`
- `parallelToolCalls`
- `maxOutputTokens`
- `temperature`
- `topP`
- `metadata`
- `extraBody`

如果你要维护多轮上下文，推荐把 `ChatMemory` 转成 `input` 传入，而不是手写结构化 `input` 数组：

```java
ChatMemory memory = new InMemoryChatMemory();
memory.addSystem("你是一个简洁的中文助手");
memory.addUser("请用一句话介绍 Responses API");

ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input(memory.toResponsesInput())
        .build();
```

完整用法见：[ChatMemory：基础会话上下文](/docs/core-sdk/chat/chat-memory)。

## 5. 与 Chat 非流式的差异

- Chat 响应主路径是 `choices[0].message`
- Responses 响应主路径是 `output`（可含 message/reasoning/function_call 等 item）

如果你要拿最终文本，通常需要从 `response.output` 中提取 `message` item 的 `output_text`。

## 6. OpenAI 请求体字段收敛说明

在 `OpenAiResponsesService` 中，SDK 会对请求体字段做白名单收敛。

含义：

- 只有协议允许字段会被发送
- `extraBody` 中不在白名单的字段会被忽略

这能减少无效字段导致的请求失败。

## 7. 非流式适用场景

- 你只关心最终结果，不关心中间事件
- 你希望简化回调处理逻辑
- 批量离线任务（摘要、改写、分类）

## 8. 常见问题

### 8.1 返回对象有内容但你看不到文本

`Response` 不是单一 `content` 字段，注意解析 `output` 列表。

### 8.2 延迟比 Chat 更明显

部分模型在 Responses 下会产出更多中间语义项，建议用流式提升体验。

下一页：`Responses（流式事件模型）`。
