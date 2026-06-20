---
sidebar_position: 22
---

# Chat vs Responses 选型

如果你是从旧目录进入这页，可以把它理解成一份“工程选型速记”，而不是另起一套理论。

canonical 对比请直接连读：[Model Access / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)。

## 1. 先给一句结论

- `Chat` 是消息式主线，provider 覆盖更广，默认也更愿意在 service 内帮你闭环工具调用
- `Responses` 是事件式主线，结构化更强，更适合 runtime、trace 和复杂交互

真正的选择标准不是“哪个更新”，而是：

- 你更想消费最终消息结果
- 还是更想消费过程中发生的状态

## 2. 两条主线的输入心智完全不同

### `Chat`

核心输入是：

- `messages`

这条链路默认假设你在推进一段对话历史。

### `Responses`

核心输入更接近：

- `input`
- `instructions`
- `previousResponseId`

它天然更像在推进一个结构化 response 协议。

所以二者的差异不只是字段名不同，而是协议哲学不同。

## 3. provider 覆盖面的差异，直接决定了默认选择

当前 `Chat` 覆盖：

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

当前 `Responses` 覆盖：

- OpenAI
- DashScope
- Doubao

这意味着：

- 追求广兼容、低迁移成本时，先选 `Chat`
- 追求事件结构化时，再优先考虑 `Responses`

## 4. 工具体系是共享的，但执行语义不同

两条主线都可以接：

- 本地 function tools
- MCP tools

但入口不同：

- `Chat` 在 service 内部直接把 `functions` / `mcpServices` 展开成 `tools`
- `Responses` 先经过 `ResponseRequestToolResolver.resolve(...)`，把已有 `tools` 与本地注册工具合并

更关键的差异在后面：

- `Chat` 默认会继续本地执行 tool loop
- `Responses` 默认只把结构化结果和事件交给上层

## 5. 自动闭环能力是最实用的分界线

### 选 `Chat`

如果你想要的是：

- 模型规划工具
- SDK 直接执行工具
- 追加 tool output
- 再自动请求下一轮

那 `Chat` 明显更顺手。

### 选 `Responses`

如果你想要的是：

- 拿到 function arguments 的形成过程
- 自己决定何时执行工具
- 在 agent runtime 里控制 trace、审批、沙箱和重试

那 `Responses` 更合适。

## 6. 流式消费对象并不是同一种东西

### `Chat` 流式

主要围绕：

- 文本输出
- reasoning 片段
- tool call 聚合
- `finishReason`

### `Responses` 流式

主要围绕：

- event type
- `outputText`
- `reasoningSummary`
- `functionArguments`
- `response` 生命周期终态

这也是为什么同样是 SSE，两条链路适合的上层消费方式完全不同。

## 7. 上下文层反而是二者最一致的地方

如果你把上下文统一存进 `ChatMemory`，那么：

- `Chat` 会投影成 `ChatMessage`
- `Responses` 会投影成 `input` items

所以多轮上下文不是选型的分界线。

真正的分界线仍然是：

- 消息式心智
- 还是事件式心智

## 8. 什么时候优先选 `Chat`

下面这些情况，先选 `Chat` 一般更稳：

- 你已有大量 chat-completions 心智或存量代码
- 你最在意 provider 覆盖
- 你只关心最终回复
- 你希望 SDK 默认帮你把工具调用闭环掉

## 9. 什么时候优先选 `Responses`

下面这些情况，更适合 `Responses`：

- 你需要区分正文、reasoning、函数参数
- 你要保留事件序列做 trace、审计或回放
- 你在做 agent runtime、workflow 或复杂交互前端
- 你不想把工具循环硬编码在 service 层

## 10. 一个务实的迁移策略

多数团队不需要“全量切换”。

更稳的做法通常是：

1. 先保留 `Chat` 作为广覆盖默认主线
2. 在新 runtime、新 agent 或高价值交互界面里引入 `Responses`
3. 在业务层抽象统一的 model client 接口，按场景路由到底层实现

这样做的好处是：

- 普通问答不必为结构化事件支付复杂度
- 复杂 runtime 也不必被消息式心智绑住

## 11. 这一页的结论

> `Chat` 和 `Responses` 在 AI4J 中不是新旧关系，而是两条不同的模型访问主线。`Chat` 更像广覆盖的消息式默认面，并且默认愿意在 service 层闭环工具调用；`Responses` 更像结构化的事件式协议面，把更多运行时决策留给上层。选型的关键不是“哪个先进”，而是你需要消费最终结果，还是过程状态。
