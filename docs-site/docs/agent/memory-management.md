---
sidebar_position: 6
---

# Memory Management

[Memory and State](/docs/agent/memory-and-state) 讲的是 memory 在 runtime 里的位置；这页只讨论一个更工程化的问题：

- 如何在 token 成本、恢复能力和推理质量之间管理上下文

换句话说，关注点不是“把消息存下来”，而是：

- 什么时候压缩
- 压缩什么
- 压缩后怎样重新进入模型上下文
- 持久化 backend 的成本和边界是什么

## 1. 管理的对象不是单条消息，而是 snapshot

关键源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/MemorySnapshot.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/MemoryCompressor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/InMemoryAgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/JdbcAgentMemory.java`

当前记忆管理的最小状态面只有两层：

- `items`
- `summary`

压缩器拿到的不是“最新一条消息”，而是一整个 `MemorySnapshot(items, summary)`。

这很重要，因为它允许你在压缩时同时决定：

- 最近原文保留多少
- 旧上下文如何折叠进 summary
- 某些工具结果是保留全文，还是只保留结论

## 2. 压缩发生在同步写路径上

这是当前设计最容易被低估的地方。

### 2.1 `InMemoryAgentMemory`

每次发生以下任一写入后，都会立即调用 `maybeCompress()`：

- `addUserInput(...)`
- `addOutputItems(...)`
- `addToolOutput(...)`

### 2.2 `JdbcAgentMemory`

每次写入 snapshot 前，也会先执行：

- `applyCompressor(...)`

这意味着压缩不是后台任务，也不是最终收尾时才触发，而是运行中的同步关键路径。

直接后果有三个：

- 压缩器异常会让当前写入失败
- 压缩耗时会直接增加当前轮延迟
- 压缩策略要按“核心运行代码”标准设计，而不是按“可选插件”标准设计

## 3. `summary` 会重新变成 system message

不管是内存实现还是 JDBC 实现，只要 summary 非空，`getItems()` 都会先插入：

```java
AgentInputItem.systemMessage(summary)
```

再返回剩余 items。

所以 summary 的真实语义不是：

- 给宿主存一段摘要备注

而是：

- 以 system-level memory item 的形式重新进入下一轮 prompt

这带来两个非常实际的判断：

- 摘要写得好，会稳定提高长任务连贯性
- 摘要写得差，会持续污染之后每一轮推理

## 4. `InMemory` 和 `JDBC` 在管理语义上有个关键差异

### 4.1 `InMemoryAgentMemory.setCompressor(...)`

只替换压缩器引用，不会立刻重压当前状态。

也就是说：

- 旧 items 不会因为你刚换了压缩器就立刻被整理
- 新压缩器要等到下一次写入时才真正生效

### 4.2 `JdbcAgentMemory.setCompressor(...)`

会立即执行：

```java
replaceSnapshot(applyCompressor(loadSnapshot()))
```

也就是说：

- 切换压缩器会立刻读取、压缩并重写当前持久化 snapshot

这是两个实现最值得在文档里显式说清的差异之一，因为它直接影响线上切换策略。

## 5. 纯窗口压缩只是最弱策略

内置 `WindowedMemoryCompressor` 只做一件事：

- item 数量超过 `maxItems` 时，仅保留最后 N 条

它不会：

- 自动生成 summary
- 合并旧摘要
- 区分不同类型工具结果

所以它本质上是一个窗口裁剪器，不是完整记忆策略。

### 5.1 它适合什么

- 本地调试
- 短任务
- 最近上下文最关键的场景

### 5.2 它不擅长什么

- 长期语义保留
- 跨阶段任务
- 大体积工具输出
- 研究型、审计型或 CodeAct 长链路任务

## 6. 更实用的记忆策略通常至少分两层

在大多数真实 Agent 里，更稳的思路通常不是“全留”或“全删”，而是：

- summary 保存阶段结论和关键约束
- recent items 保存最近可执行细节

对工具密集型任务，还应再加一层判断：

- 哪些工具结果要保留全文
- 哪些工具结果只该保留结论

例如：

- `read_file` 读到的大段源码，通常不应长期保留全文
- 数据库检索出的关键结论、失败原因、下一步约束，通常应进入 summary

## 7. 选择 backend，不只是选“能不能持久化”

### 7.1 `InMemoryAgentMemory`

优点：

- 延迟低
- 实现简单
- 适合本地单进程和短任务

限制：

- 进程退出即丢失
- 不能天然跨实例恢复

### 7.2 `JdbcAgentMemory`

优点：

- 可以跨进程恢复
- `sessionId` 能作为稳定恢复边界
- 能接现有数据库与连接池

代价：

- 每次写入先 `loadSnapshot()`
- 然后整份 `replaceSnapshot(...)`
- 会先删旧记录，再重写 summary 与 items

这说明 JDBC 实现当前优先的是：

- 语义一致性
- 恢复简单性

而不是：

- 增量写最优
- 高频写吞吐最优

如果你的任务会产生很多工具输出，或者一步内频繁写 memory，必须单独评估写放大成本。

## 8. `sessionId` 才是持久化隔离的真正边界

对 `JdbcAgentMemory` 来说，是否新建对象实例不是最重要的，真正决定隔离的是：

- `sessionId`

如果两个 session 指向同一个 `sessionId`，它们管理的就是同一份状态。

所以长期任务里，`sessionId` 最好对齐稳定业务标识，例如：

- 对话线程 ID
- 工单 ID
- 任务 ID
- 用户会话 ID

## 9. 管理策略里最常见的几个反模式

### 9.1 只做窗口，不保留结论

这样初期最省事，但一旦任务变长，Agent 会开始“忘掉为什么之前这么做”。

### 9.2 把所有工具输出都等价对待

检索摘要、代码 diff、超长文件内容、错误堆栈，对后续推理价值完全不同。统一保留或统一裁掉，通常都不合理。

### 9.3 让 summary 变成流水账

summary 如果只是“本轮做了什么”的机械清单，而不是“当前目标、关键结论、未解决问题、约束”，它对后续推理帮助很有限。

### 9.4 压缩策略没有可观测性

如果你完全不知道每次压缩删掉了什么、保留了什么，后续出现“怎么突然变笨了”的问题会非常难排。

## 10. 当前实现的可观测空缺

`AgentEventType` 虽然包含 memory 相关事件语义，但默认 memory 实现并不会主动把压缩细节打出来。

所以如果你真的关心记忆质量，通常应该在自定义 memory 或 compressor 里主动记录：

- 压缩前 item 数
- 压缩后 item 数
- summary 长度
- 被裁掉的工具结果规模
- 压缩耗时

否则 trace 里只看到最终回答，很难把质量退化归因到 memory policy。

## 11. 自定义压缩器时先守住这几个原则

一个可用的 `MemoryCompressor`，至少要明确回答：

1. 什么时候压缩
2. 哪些信息进入 summary
3. 哪些信息必须保留在 recent items
4. summary 是覆盖、累加还是阶段性重写
5. 压缩失败时是否允许本轮直接失败

对工具驱动任务，一个实用的最低标准通常是：

- summary 里至少保留当前目标
- 已完成的关键步骤
- 关键外部观察结论
- 尚未解决的问题
- 后续下一步约束

## 12. 和 `Memory and State` 的分工

如果你要理解：

- runtime 什么时候读写 memory
- 工具结果如何回灌
- session 隔离怎么实现

先读 [Memory and State](/docs/agent/memory-and-state)。

如果你要决定：

- 压缩策略怎么设计
- backend 怎么选
- 长任务为什么越跑越歪
- summary 应该长什么样

这页才是更直接的参考。

## 13. 继续阅读

1. [Memory and State](/docs/agent/memory-and-state)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Runtime Implementations](/docs/agent/runtime-implementations)
4. [Trace Observability](/docs/agent/trace-observability)
