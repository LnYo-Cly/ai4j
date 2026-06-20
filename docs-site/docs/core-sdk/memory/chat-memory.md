# Chat Memory

`ChatMemory` 是 AI4J 基座里一个非常核心、但容易被低估的对象。  
它真正做的不是“帮你存一份 messages”，而是 **把多轮会话事实组织成可投影、可裁剪、可快照、可恢复的统一上下文抽象**。

## 1. 先看真实契约

`ChatMemory.java` 当前定义的核心能力包括：

- `addSystem(...)`
- `addUser(...)`
- `addAssistant(...)`
- `addAssistantToolCalls(...)`
- `addToolOutput(...)`
- `add(...)`
- `addAll(...)`
- `getItems()`
- `toChatMessages()`
- `toResponsesInput()`
- `snapshot()`
- `restore(...)`
- `clear()`

只看这组方法就能知道，它不是某个 provider 的附属工具，而是会话状态的正式抽象层。

## 2. 记录的不是文本，而是会话事实

`ChatMemoryItem` 决定了这一层到底保存什么。

它当前能承载：

- role
- 文本
- 图片 URL
- assistant tool calls
- tool output 对应的 `toolCallId`
- 是否为摘要条目

这意味着 memory 可以同时覆盖：

- 普通文本对话
- 多模态用户输入
- assistant 发起的 tool call
- tool 执行后回写的结果

所以这层保存的是“模型上下文事实”，不是“最后一轮问答文本”。

## 3. 为什么它是 `Chat` 和 `Responses` 的共享基座

`ChatMemoryItem` 里有两个关键方法：

- `toChatMessage()`
- `toResponsesInput()`

它们对应两种不同协议投影：

- `Chat` 路径投影成 `ChatMessage`
- `Responses` 路径投影成 `message` / `function_call_output` 等输入项

同一份历史不需要被业务层维护两套结构，这正是 `ChatMemory` 的价值所在。

## 4. `InMemoryChatMemory` 的真实行为

默认实现是：

- `InMemoryChatMemory`

它的几个关键行为很直接：

- 内部持有 `List<ChatMemoryItem>`
- `add(...)` 时会复制条目，避免外部对象直接共享
- 每次新增后都会立即应用当前 policy
- `getItems()`、`snapshot()`、`toChatMessages()` 都返回重新组织后的结果

这意味着它不是“原封不动地堆列表”，而是每次写入后都会把当前保留策略真正执行一遍。

## 5. `JdbcChatMemory` 的真实行为

`JdbcChatMemory` 是正式持久化实现，不是演示类。

构造时它要求：

- `sessionId`
- `dataSource` 或 `jdbcUrl`

默认行为包括：

- `tableName = "ai4j_chat_memory"`
- `initializeSchema = true`
- policy 默认仍然是 `UnboundedChatMemoryPolicy`

它的写入策略也值得明确写出来：

- 读取当前会话所有条目
- 合并新条目
- 应用 policy
- 用事务先删旧记录，再按 `item_index` 重新插入整段会话

这个设计的好处是恢复简单、顺序稳定；代价是它偏向会话一致性，而不是高频增量更新性能。

## 6. policy 是如何真正生效的

### `UnboundedChatMemoryPolicy`

最简单，基本等于复制当前会话，不做裁剪。

### `MessageWindowChatMemoryPolicy`

这不是“简单保留最后 N 条数组元素”。

它会：

- 从尾部开始统计最近的非 system 消息
- 尽量保留 `system` 项
- 丢弃超出窗口的较早非 system 条目

测试里已经验证了这一点：即使窗口裁剪发生，`system` 消息仍会被保住。

### `SummaryChatMemoryPolicy`

这是更重的一条策略链。

它会：

- 跳过非摘要 `system` 项，不把它们纳入摘要对象
- 当可摘要消息数超过 `summaryTriggerMessages` 时触发摘要
- 保留最近 `maxRecentMessages`
- 把更早的消息交给 `ChatMemorySummarizer`
- 生成一条 `summary=true` 的新条目插回会话

它不是简单“把前面拼成一段字符串”，而是显式引入了摘要器接口与摘要请求对象：

- `ChatMemorySummarizer`
- `ChatMemorySummaryRequest`

这使得摘要逻辑可以由业务自己决定，而不是被 SDK 写死。

## 7. 快照与恢复意味着什么

`snapshot()` / `restore(...)` 的存在让这一层不只是“当前会话容器”，还支持：

- 会话回放
- 临时分支试验
- 手动 checkpoint
- 持久化恢复

这里的 `ChatMemorySnapshot` 只是复制 `ChatMemoryItem` 列表，不承担额外语义。  
它的价值在于把“当下会话状态”稳定冻结下来。

## 8. 多模态和工具结果如何进入 memory

### 多模态用户输入

`addUser(String text, String... imageUrls)` 会把文本和图片 URL 记录成同一个 `ChatMemoryItem`。  
投影到 `Chat` 时会变成 `Content.MultiModal`，投影到 `Responses` 时会变成 `input_text` / `input_image` 结构。

### 工具结果

assistant 的 tool calls 和 tool outputs 是分开记录的：

- `addAssistant(..., toolCalls)`
- `addToolOutput(toolCallId, output)`

这使得模型下一轮看到的是一个完整链条：

- assistant 曾请求过什么工具
- 那个工具返回了什么

## 9. 使用这层时最常见的误区

### 把 `InMemoryChatMemory` 当成跨进程持久层

它只适合单 JVM 生命周期内的会话，不适合服务重启后恢复。

### 业务层自己手工 `subList`

更稳的方式是显式使用 `ChatMemoryPolicy`。  
业务层手工截列表很容易破坏 system、summary、tool output 之间的结构。

### 以为 memory 会自动治理工具

它可以记录 tool call 和 tool output，但不会决定工具能不能执行，也不会处理审批或副作用补偿。

## 10. 一个更准确的定义

> `ChatMemory` 在 AI4J 里是统一会话事实层。它把多轮对话、多模态输入、tool call、tool output、窗口裁剪、摘要压缩和快照恢复收进同一套契约里，并允许同一份历史同时投影到 `Chat` 和 `Responses` 两条访问主线。
