# Chat vs Responses

这是整个基座里最重要的选型问题之一。真正的区别不在“哪个更先进”，而在**你想消费什么样的模型输出语义**。

## 1. 先给一句结论

- `Chat`：消息式接口，迁移成本低，provider 覆盖广，适合首调
- `Responses`：事件式接口，结构化更强，适合 runtime 和复杂交互

可以先从这一组差异建立整体判断。

## 2. 从输入心智看

### `Chat`

- 中心对象是 `messages`
- 更贴近传统 chat completions
- 一轮请求更像一次完整对话

### `Responses`

- 中心对象是 `input`
- 输出更像事件流和 response item
- 更适合“模型正在发生什么”的细粒度消费

所以两者的差异，不是参数名小改，而是**接口哲学不同**。

## 3. 从工具集成看

两者都能挂：

- `functions`
- `mcpServices`

但它们对工具调用的消费方式不同：

- `Chat`：更偏消息式 tool call
- `Responses`：更偏 function arguments / response item 事件流

这就是为什么同样都支持工具，`Responses` 更适合拿去做 runtime。

## 4. 从流式心智看

### `Chat` 流式更像什么

- 文本 delta
- tool call 片段
- reasoning 片段

### `Responses` 流式更像什么

- output_text delta
- reasoning summary delta
- function_call_arguments delta
- response 级状态聚合

从流式模型看：

- `Chat` 流式更像“消息在长出来”
- `Responses` 流式更像“状态机在推进”

## 5. 从 provider 覆盖看

当前 AI4J 中：

- `Chat` 的 provider 覆盖更广
- `Responses` 的 provider 覆盖更聚焦

这意味着：

- 如果你更重视快速接通更多 provider，优先 `Chat`
- 如果你更重视结构化事件语义，优先 `Responses`

## 6. 一个很实用的决策表

| 场景 | 更推荐 |
|------|--------|
| 第一次接 AI4J | `Chat` |
| 迁移已有 chat-completions 代码 | `Chat` |
| 普通多轮文本问答 | `Chat` |
| 需要细粒度 function args / reasoning 事件 | `Responses` |
| 搭 Agent / Tool runtime | `Responses` |
| 做结构化 UI / trace 视图 | `Responses` |

## 7. 推荐的迁移路径

如果你现在不确定，通常可以采用这条迁移路径：

1. 先把 `Chat` 跑通
2. 把 memory、tool、streaming 的基本心智立住
3. 当需求开始偏事件和 runtime，再升级到 `Responses`

这条路线的好处是：

- 先低成本建立基座理解
- 再按需求升级，而不是一开始就把复杂度拉满

## 8. 设计摘要

`Chat` 和 `Responses` 的区别不是新旧之分，而是消息式接口和事件式接口之分。AI4J 让两者共享工具和 memory 基座，但 `Chat` 更适合通用接入，`Responses` 更适合做结构化 runtime。

## 9. 继续阅读

- [Model Access / Chat](/docs/core-sdk/model-access/chat)
- [Model Access / Responses](/docs/core-sdk/model-access/responses)
