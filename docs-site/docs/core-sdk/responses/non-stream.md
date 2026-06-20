---
sidebar_position: 20
---

# Responses（非流式）

这一页讲旧入口下的 `Responses` 非流式调用。

如果你还没有建立这条主线的整体心智，建议先读：[Model Access / Responses](/docs/core-sdk/model-access/responses)。

## 1. 先给一句工程结论

`Responses` 非流式并不是 `Chat` 把 `messages` 改名成 `input`。

它的根本差异在于：

- 请求是结构化 response 协议
- 返回对象首先是 `Response`
- 输出主路径是 `output` items，不是 `choices[0].message`
- 工具解析和 payload 收敛发生在请求发送之前
- service 本身不负责自动本地 tool loop

## 2. 关键源码入口

建议重点看：

- `platform/openai/response/entity/ResponseRequest.java`
- `platform/openai/response/entity/Response.java`
- `platform/openai/response/OpenAiResponsesService.java`
- `tool/ResponseRequestToolResolver.java`
- `service/factory/AiService.java`

其中最值得注意的不是数据类本身，而是：

- `ResponseRequestToolResolver.resolve(...)`
- `OpenAiResponsesService.buildOpenAiPayload(...)`

前者定义“本地工具如何并入请求”，后者定义“最终哪些字段真的会发给 provider”。

## 3. provider 覆盖为什么比 `Chat` 更窄

当前 `AiService.createResponsesService(...)` 支持：

- `OPENAI`
- `DOUBAO`
- `DASHSCOPE`

这意味着 `Responses` 当前更像：

- 结构化能力主线
- runtime 友好主线

而不是 provider 覆盖最广的默认接入面。

如果你的第一目标是兼容更多平台，先看 `Chat` 往往更省成本。

## 4. 一次 `create(...)` 调用到底会发生什么

以 `OpenAiResponsesService.create(...)` 为例，执行链大致是：

1. 强制把请求改成非流式：`stream=false`
2. 清掉 `streamOptions`
3. 调用 `ResponseRequestToolResolver.resolve(request)`
4. 把 `ResponseRequest` 收敛成 provider payload
5. 发起单次 HTTP 请求并反序列化为 `Response`

这里最重要的差异是第 3 步和第 4 步。

它们说明：

- 本地注册字段不会裸传给 provider
- 甚至连 `extraBody` 也不会无条件原样透传

## 5. `ResponseRequestToolResolver` 做的是“合并”，不是“覆盖”

这是旧入口文档里最值得说清的一点。

`ResponseRequestToolResolver.resolve(...)` 当前行为是：

1. 如果 request 已经有 `tools`，先保留
2. 再把 `functions` 和 `mcpServices` 解析出的工具追加进去
3. 返回一个新的 request 副本

这意味着 `Responses` 的工具入口可以同时包含：

- 你手工构造的 `tools`
- 本地 function registry
- MCP service registry

所以它比 `Chat` 更像一个“请求装配器”，而不是单一来源的工具展开器。

## 6. `buildOpenAiPayload(...)` 为什么重要

`OpenAiResponsesService` 当前不会把整个 `ResponseRequest` 原样序列化出去。

它只显式写入允许字段，例如：

- `model`
- `input`
- `instructions`
- `max_output_tokens`
- `parallel_tool_calls`
- `previous_response_id`
- `reasoning`
- `store`
- `stream`
- `stream_options`
- `text`
- `tool_choice`
- `tools`
- `truncation`
- `user`

并且 `extraBody` 只有命中允许名单的字段才会被补进 payload。

这带来的直接后果是：

- 协议更稳定
- 非法字段更少
- 但你不能把 `extraBody` 当成“任意透传口”

## 7. 一个最小调用示例

```java
IResponsesService responsesService = aiService.getResponsesService(PlatformType.DOUBAO);

ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input("请用一句话介绍 Responses API")
        .instructions("用中文输出")
        .maxOutputTokens(256)
        .build();

Response response = responsesService.create(request);
```

如果你只是想做：

- 摘要
- 改写
- 结构化抽取
- 批量离线任务

且只关心最终结果，这种非流式路径通常已经够用。

## 8. 为什么多轮上下文更适合走 `ChatMemory`

`Responses` 的 `input` 可以是复杂结构，但手写这套结构通常维护成本很高。

更稳的方式是：

```java
ChatMemory memory = new InMemoryChatMemory();
memory.addSystem("你是一个简洁的中文助手");
memory.addUser("请用一句话介绍 Responses API");

ResponseRequest request = ResponseRequest.builder()
        .model("doubao-seed-1-8-251228")
        .input(memory.toResponsesInput())
        .build();
```

这样做的价值不是少写几行，而是：

- 多轮消息
- 多模态内容
- tool transcript

都能按统一事实层投影到 `Responses`。

## 9. 为什么 `Responses` 非流式不会像 `Chat` 那样自动把工具跑完

当前 `OpenAiResponsesService.create(...)` 的职责是：

- 解析工具
- 发送请求
- 返回 `Response`

它没有在 service 内部做那种：

- 收到 function call
- 本地执行工具
- 回填输出
- 自动发下一轮

的默认闭环。

这不是缺功能，而是分层选择：

- `Responses` 更偏向把过程状态交给上层 runtime 决策
- `Chat` 更偏向在 service 层直接帮你闭环

## 10. 调用后你真正该解析什么

在 `Responses` 里，不要再带着 `choices[0].message.content` 的心智找结果。

你真正该关心的是：

- `response.getOutput()`
- 各个 output item 的 `type`
- item content 里的文本 part

如果你只想抽最终文本，就写一个明确的提取函数，而不是假设存在单一 `content` 字段。

## 11. 常见失败路径

### 11.1 你传了 `extraBody`，但 provider 端看不到

优先排查：

- 字段名是否在当前 service 允许名单里
- 该字段是否已经被标准字段占用

### 11.2 工具没进请求

优先排查：

- `functions` / `mcpServices` 是否真的有值
- `tools` 是否被正确合并
- 你是不是误把本地注册字段当成最终 payload

### 11.3 返回对象有东西，但你“看不到文本”

这通常不是模型没回答，而是你还在按 `Chat` 的单 message 心智取值。

## 12. 还有哪些 lifecycle 能力别忽略

当前 `IResponsesService` 还提供了：

- `retrieve(responseId)`
- `delete(responseId)`

这说明 `Responses` 在设计上天然更接近“可追踪的 response 资源”，而不只是“一次聊天返回一段字符串”。

## 13. 这一页的结论

> AI4J 的 `Responses` 非流式是一条结构化 response 主线。请求会先经过 `ResponseRequestToolResolver` 合并工具，再由 service 按允许字段构建 provider payload，最终返回 `Response` 而不是单条 assistant message。它更适合结构化输出和 runtime 编排，但不会像 `Chat` 那样默认替你完成本地工具闭环。
