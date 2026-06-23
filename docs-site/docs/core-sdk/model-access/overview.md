# Model Access 总览

`Model Access` 这一章讲的不是“AI4J 支持哪些模型名字”，而是 **模型请求在 AI4J 基座里如何被建模、投影、发送、流式消费和回读**。

这一层的关键源码锚点主要是：

- `platform/openai/chat/entity/ChatCompletion.java`
- `platform/openai/response/entity/ResponseRequest.java`
- `platform/anthropic/chat/entity/AnthropicChatCompletion.java`
- `platform/openai/chat/OpenAiChatService.java`
- `platform/openai/response/OpenAiResponsesService.java`
- `platform/anthropic/chat/AnthropicMessagesService.java`
- `platform/anthropic/chat/AnthropicChatService.java`
- `listener/SseListener.java`
- `listener/ResponseSseListener.java`
- `service/IMessagesService.java`
- `service/factory/AiService.java`

## 1. 这一章在 Core SDK 里的位置

如果 `service-entry-and-registry` 讲的是“从哪个 service 入口拿能力”，那 `model-access` 讲的就是：

- 请求对象长什么样
- provider 适配层在发送前做了什么
- 流式返回如何被聚合
- `Chat` 和 `Responses` 分别适合什么运行时

它仍然是 `ai4j/` 基座层的问题，不是 Agent 或 Coding Agent 的上层专属话题。

## 2. 这一章真正覆盖什么

这一章主要覆盖五个问题：

1. 为什么 AI4J 同时保留 `Chat` 与 `Responses`
2. 请求对象里哪些字段是主语义，哪些只是辅助注册信息
3. 流式结果在本地如何被监听器聚合
4. 多模态输入如何投影到两条主线
5. provider 差异在基座层被保留到了什么程度

它不负责讲：

- tool 执行细节
- MCP 协议接入细节
- agent loop
- coding runtime

这些要切去 `tools`、`mcp` 或上层模块文档。

## 3. 这章最先要分清的边界

第一次进入这一章，最重要的不是先看某个 provider，而是先分清 AI4J 内部有三条不同的模型访问主线，分别对应三族原生协议：

### `Chat`

- 以 `messages` 为中心
- 更贴近传统 chat completions 心智
- provider 覆盖最广
- 内建了自动 tool loop 与流式 tool call 聚合

### `Responses`

- 以 `input` 和事件序列为中心
- 更贴近结构化 response item / event 心智
- provider 覆盖更聚焦
- 更适合 runtime 侧状态消费

### `Messages`

- 以 Anthropic Messages 协议为中心（顶层 `system`、`content blocks`、`tool_use` / `tool_result`、`thinking`）
- `IMessagesService` 是原生一等公民：Anthropic 格式进、Anthropic 格式出，零 OpenAI 转换
- 同时有一个统一适配器 `AnthropicChatService`（实现 `IChatService`），把 OpenAI Chat 请求翻译成 Anthropic Messages，让只想用 `IChatService` 的上层无感接入
- 适合需要原生 Anthropic 语义（thinking、content blocks）或使用 coding-plan key（智谱 / Minimax 的 Anthropic 端点）的场景

很多后续差异，包括 streaming、多模态、工具解析方式，本质上都从这里分叉。

## 4. 当前 provider 覆盖并不完全对称

`AiService.createChatService(...)` 当前能创建的 `Chat` provider 包括：

- OpenAI
- Zhipu
- DeepSeek
- Moonshot
- Hunyuan
- Lingyi
- Ollama
- Minimax
- Baichuan
- DashScope
- Doubao

而 `AiService.createResponsesService(...)` 当前只覆盖：

- OpenAI
- Doubao
- DashScope

这意味着在 AI4J 里：

- `Chat` 是更广覆盖的默认主线
- `Responses` 是更结构化、但 provider 覆盖更聚焦的主线
- `Messages`（Anthropic 格式）原生覆盖 Anthropic 端点；智谱、Minimax 的 **coding-plan key** 也走 Anthropic 格式端点，因此通过 `Messages` 或其统一适配器 `AnthropicChatService` 接入

这不是文档叙述偏好，而是当前工厂实现给出的事实。

## 5. AI4J 如何处理“统一请求”与“provider 差异”

AI4J 的请求对象策略不是简单追求字段最少，而是把主语义固定下来，再把差异留给适配层。

例如：

- `ChatCompletion` 有 `model / messages / stream / tools / toolChoice / responseFormat`
- `ResponseRequest` 有 `model / input / stream / tools / toolChoice / reasoning / truncation`
- 两者都有 `functions` 与 `mcpServices` 这类本地注册辅助字段
- 两者都有 `extraBody` 承接额外 payload

关键点在于：

- `functions` 和 `mcpServices` 不会原样发给 provider
- `tools` 才是最终真正进入 provider payload 的字段

所以读这一章时，要把“本地注册字段”和“最终 provider 字段”分开理解。

## 6. 三条主线都不是单纯文本接口

`Chat` 不是“只会返回一条字符串”：

- 同步调用里可以自动执行 tool calls
- 流式调用里 `SseListener` 会聚合 reasoning、tool calls、usage、finish reason

`Responses` 也不是“只会返回一堆事件”：

- 非流式会返回完整 `Response`
- 流式时 `ResponseSseListener` 会同时聚合 `events`、`outputText`、`reasoningSummary`、`functionArguments` 和最终 `response`

`Messages` 同样不只是文本：content blocks 里会带 `thinking`（映射成统一 `reasoningContent` / agent `reasoningText` / 流式 `onReasoningDelta`）、`tool_use` / `tool_result`，统一适配器还能自动跑 tool loop 并把结果回传成 `tool_result`。

这意味着三条主线都已经足以支撑中等复杂度运行时，只是适合的消费方式不同。

## 7. 多模态也属于这一层，而不是 Tool 或 MCP

AI4J 把图文输入纳入了统一会话抽象：

- `ChatMemory.addUser(String text, String... imageUrls)`
- `ChatMemoryItem.toChatMessage()`
- `ChatMemoryItem.toResponsesInput()`

同一份会话事实可以：

- 投影成 `ChatMessage + Content.MultiModal`
- 或投影成 `Responses` 的 `input_text / input_image`

这说明多模态在 AI4J 里是请求协议问题，不是外部能力接入问题。

## 8. 推荐阅读顺序

建议按下面顺序读：

1. [Chat](/docs/core-sdk/model-access/chat)
2. [Responses](/docs/core-sdk/model-access/responses)
3. [Messages](/docs/core-sdk/model-access/messages)
4. [Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
5. [Streaming](/docs/core-sdk/model-access/streaming)
6. [Multimodal](/docs/core-sdk/model-access/multimodal)
6. [Request and Response Conventions](/docs/core-sdk/model-access/request-and-response-conventions)

## 9. 这一页的结论

> `Model Access` 在 AI4J 里讲的是“请求如何被建模并送进 provider”，不是“模型能做什么”。当前基座保留了两条清晰主线：`Chat` 负责更广覆盖的消息式访问，`Responses` 负责更结构化的事件式访问；二者共享统一工具与多模态基座，但并不应被看成同一接口的两种皮肤。
