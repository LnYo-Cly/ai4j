# Chat vs Responses

这是 AI4J 基座里最重要的选型问题之一。

真正的区别不在“哪个更新”，而在 **你到底想消费什么样的模型输出语义，以及你更在意 provider 覆盖还是事件结构**。

## 1. 先给一句结论

- `Chat`：消息式主线，provider 覆盖广，适合先打通、也适合自动 tool loop
- `Responses`：事件式主线，结构化更强，适合 runtime、trace 和复杂交互

如果没有更强约束，先选 `Chat` 通常更稳；如果你已经在做 runtime 级状态消费，再认真看 `Responses`。

## 2. 从输入中心看，二者的心智不同

### `Chat`

中心对象是：

- `messages`

一次请求更像：

- 带着当前对话上下文发起一次完整对话

### `Responses`

中心对象是：

- `input`
- `instructions`
- `previousResponseId`

一次请求更像：

- 在一个更结构化的 response 协议里推进状态

所以两者的差异不是参数名微调，而是接口哲学不同。

## 3. 从 provider 覆盖看，`Chat` 更像默认主线

当前 `AiService.createChatService(...)` 支持：

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

而 `AiService.createResponsesService(...)` 当前只支持：

- OpenAI
- Doubao
- DashScope

这意味着如果你的第一优先级是：

- 更广 provider 覆盖
- 更低迁移成本

那 `Chat` 更接近默认主线。

## 4. 从工具集成看，二者共享基座，但接入点不同

两者都能挂：

- `functions`
- `mcpServices`
- `toolChoice`
- `parallelToolCalls`

但接入路径不同：

### Chat

在 `OpenAiChatService` 内直接调用：

`ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices())`

### Responses

先走：

`ResponseRequestToolResolver.resolve(request)`

再把结果写到 `request.tools`

也就是说：

- 工具解析基座是共享的
- 接入点和后续运行时语义并不相同

## 5. 从自动执行心智看，`Chat` 更主动

`Chat` 当前在 service 层内部就支持自动 tool loop：

1. 收到 `tool_calls`
2. 通过 `ToolUtil.invoke(...)` 执行工具
3. 追加 tool output message
4. 继续下一轮请求

如果不启用 `passThroughToolCalls`，这条链在同步和流式里都会继续往下跑。

而 `Responses` 当前更倾向于：

- 解析好 tools
- 把事件和 response 聚合出来
- 把后续编排留给上层 runtime

所以如果你想要“SDK 先帮我把工具跑一轮再说”，`Chat` 更顺手。

## 6. 从流式语义看，二者消费对象完全不同

### Chat 流式

核心聚合器是 `SseListener`，重点围绕：

- `output`
- `reasoningOutput`
- `toolCalls`
- `finishReason`

它更像“消息流 + tool call 聚合”。

### Responses 流式

核心聚合器是 `ResponseSseListener`，重点围绕：

- `events`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- `response`

它更像“事件状态机 + 最终 response 聚合”。

如果你的 UI 或 runtime 需要区分“文本 delta”和“function argument delta”，`Responses` 会更自然。

## 7. 从多模态投影看，二者共享会话基座

这点二者反而是一致的。

同一份 `ChatMemoryItem`：

- 在 `Chat` 中会变成 `Content.MultiModal`
- 在 `Responses` 中会变成 `input_text / input_image`

所以多模态不是二者的根本分野；真正的分野仍然是消息式心智还是事件式心智。

## 8. 什么时候优先选 `Chat`

下面这些情况通常先选 `Chat`：

- 第一次接 AI4J
- 代码迁移自 chat-completions
- provider 覆盖比事件结构更重要
- 希望 SDK 帮你自动闭环执行 tool calls
- 上层暂时不需要复杂状态机

## 9. 什么时候优先选 `Responses`

下面这些情况更适合 `Responses`：

- 需要 event 级别消费
- 需要 reasoning 单独观察
- 需要 function arguments delta
- 需要完整 response 生命周期状态
- 正在做 agent / coding / trace / structured UI runtime

## 10. 一个简单的判断方法

如果你更关心：

- “模型最后说了什么”

先选 `Chat`。

如果你更关心：

- “模型在过程中发生了什么”

先看 `Responses`。

## 11. 这一页的结论

> `Chat` 和 `Responses` 在 AI4J 中不是新旧关系，而是两条不同的模型访问主线。`Chat` 以消息和自动 tool loop 为中心，更适合广覆盖和快速接入；`Responses` 以事件和结构化 response 为中心，更适合 runtime、trace 和复杂交互。真正的选型标准不是“哪个更先进”，而是你要消费的是消息结果，还是过程状态。
