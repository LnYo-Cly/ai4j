# Memory and State

在 `Agent` 架构里，state 不是附属功能，而是 runtime 能否持续运行的前提。

如果没有稳定的状态语义，下面这些能力都无法成立：

- 多轮推理
- 工具结果回灌
- CodeAct 代码执行闭环
- session 隔离
- 长任务压缩与恢复

这页关注的不是“怎么把聊天记录存下来”，而是 `ai4j-agent` 如何把运行时状态建模为 `AgentMemory`，并让 runtime、session、压缩器、持久化后端围绕它协同工作。

## 1. 它解决什么问题

仅靠消息列表做上下文时，系统通常会遇到四类问题：

- 用户输入、模型输出、工具输出没有统一存储语义
- 下一轮 prompt 不知道该从哪里重新组装
- 长会话无法压缩，只能无限追加
- 同一个 Agent 难以安全地派生多个独立 session

`ai4j-agent` 的方案是把“运行时上下文”从 runtime 主循环中抽离出来，交给 `AgentMemory` 管理。runtime 每一步都读写 memory，而不是自己持有一份隐式历史。

## 2. 设计原则

### 2.1 Memory 是状态源，不是日志附件

在 `BaseAgentRuntime.runInternal()` 中，memory 不是只在开头写入一次，也不是只在结束时保存一次。它是每一轮循环都要参与的状态源：

- 输入进入 memory
- 模型输出进入 memory
- 工具输出进入 memory
- 下一轮 prompt 从 memory 重建

因此它和普通“消息历史日志”不是一个层级的东西。

### 2.2 状态以 item 形式保留，而不是直接拼接长字符串

`AgentMemory` 存的是 `List<Object>` 形式的 items，而不是一整段已经拼好的 prompt 文本。

这样做的好处是：

- runtime 可以统一构造 `AgentPrompt`
- 工具结果可以用结构化 item 表示
- 压缩器可以基于 item 级别裁剪和摘要
- 不同模型协议可以复用同一份 memory 语义

### 2.3 Summary 与 recent items 分离

memory 不是只能“全量保留”或“直接丢弃”。`summary` 和 `items` 被拆成两个层次：

- `summary` 保存压缩后的长期语义
- `items` 保存当前窗口内的原始上下文

这让“历史摘要 + 最近窗口”成为基础实现路径，而不需要把所有长任务都退化成一堆原始消息。

### 2.4 Session 隔离通过 memory supplier 实现

`Agent.newSession()` 的关键动作不是复制 runtime，而是为新 session 换一份 memory。

这意味着 session 是否真正隔离，本质上取决于：

- `memorySupplier` 是否每次返回新实例
- 持久化 memory 是否使用不同 `sessionId`

### 2.5 压缩是 memory 的职责，不是 runtime 的职责

`InMemoryAgentMemory` 和 `JdbcAgentMemory` 都允许挂 `MemoryCompressor`，而 runtime 并不负责决定怎么裁剪历史。

这样做的好处是：

- 压缩策略和执行策略分离
- ReAct / CodeAct / Team 可以共享同一套 memory 策略
- 压缩实现可以按业务更换，而不必改 runtime

## 3. 核心抽象

### 3.1 `AgentMemory`

源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/AgentMemory.java`

接口定义了三类写入和三类读取能力：

```java
public interface AgentMemory {
    void addUserInput(Object input);
    void addOutputItems(List<Object> items);
    void addToolOutput(String callId, String output);
    List<Object> getItems();
    String getSummary();
    void clear();
}
```

这说明 Agent 只关心三种状态输入：

- 用户输入
- 模型输出 items
- 工具输出

### 3.2 `InMemoryAgentMemory`

源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/InMemoryAgentMemory.java`

它是默认实现，适合本地开发和单进程短任务。核心结构包括：

- `items`
- `summary`
- `compressor`

每次写入后都会调用 `maybeCompress()`。

### 3.3 `JdbcAgentMemory`

源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/JdbcAgentMemory.java`

它提供持久化 memory，实现特点是：

- 通过 `sessionId` 区分会话
- 支持 `DataSource` 或 `jdbcUrl`
- 支持同样的 `MemoryCompressor`
- 读写语义尽量和 `InMemoryAgentMemory` 保持一致

### 3.4 `MemoryCompressor`

源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/MemoryCompressor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/WindowedMemoryCompressor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/MemorySnapshot.java`

压缩器处理的不是单个 item，而是一份 snapshot：

- `items`
- `summary`

这为“保留最近窗口 + 合成历史摘要”提供了标准插槽。

### 3.5 `AgentSession`

源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentSession.java`

`AgentSession` 不是另一套 runtime，而是同一个 runtime + 新的 context/memory 组合。

## 4. 运行时读写流程

### 4.1 用户输入如何进入状态

`BaseAgentRuntime.runInternal()` 在收到 `AgentRequest` 后，会先调用：

```java
memory.addUserInput(request.getInput())
```

如果输入是 `String`，`InMemoryAgentMemory` 会把它转成 `AgentInputItem.userMessage(...)`。

### 4.2 模型输出如何进入状态

模型执行完成后，如果 `AgentModelResult` 包含 `memoryItems`，runtime 会调用：

```java
memory.addOutputItems(modelResult.getMemoryItems())
```

这意味着并不是只有用户消息和工具消息会被保留，模型侧标准化后的输出 item 也会进入同一状态源。

### 4.3 工具结果如何进入状态

工具执行完成后，runtime 会调用：

```java
memory.addToolOutput(callId, output)
```

`InMemoryAgentMemory` 会把它转成：

- `AgentInputItem.functionCallOutput(callId, output)`

因此工具结果不是额外旁路数据，而是正式参与后续 prompt 的上下文 item。

### 4.4 下一轮 prompt 如何读取状态

`BaseAgentRuntime.buildPrompt(...)` 会从：

```java
memory.getItems()
```

重新取回上下文。

这一步非常关键，因为它说明 Agent runtime 不持有“内部隐藏历史”，所有可见状态都必须能从 memory 重新构造出来。

## 5. `summary` 的真实语义

很多系统会把 summary 当成独立字段保存，但不清楚它何时真正进入模型输入。

在 `InMemoryAgentMemory.getItems()` 和 `JdbcAgentMemory.getItems()` 里，summary 的处理方式是：

- 如果 summary 为空，直接返回 items
- 如果 summary 不为空，先插入一条 `systemMessage(summary)`，再追加 items

这意味着：

- summary 不是只给 UI 看
- summary 会重新进入模型上下文
- 它的提示强度等价于一条 system-level memory item

这也是为什么压缩质量会直接影响后续推理质量。

## 6. Session 隔离是怎么实现的

`Agent.newSession()` 的代码语义很简单：

1. 从 `memorySupplier` 获取一份新 memory
2. 用 `baseContext.toBuilder().memory(memory).build()` 构造 session context
3. 返回新的 `AgentSession`

这意味着：

- runtime 复用
- modelClient 复用
- tool registry / executor 复用
- memory 独立

这也是当前 Agent session 隔离的核心机制。

### 6.1 需要特别注意的地方

如果你的 `memorySupplier` 返回的是共享单例，多个 session 仍然会串状态。

如果你使用 `JdbcAgentMemory`，即使实例是新建的，只要 `sessionId` 一样，也仍然会共享同一份持久化状态。

## 7. InMemory 与 JDBC 的实现差异

### 7.1 `InMemoryAgentMemory`

特点：

- 全部状态保存在进程内
- 写入成本低
- 进程退出后状态丢失
- 压缩直接在写入路径同步执行

适合：

- 本地开发
- 单进程短任务
- 快速验证 runtime 行为

### 7.2 `JdbcAgentMemory`

特点：

- 会话状态按 `sessionId` 落库
- 支持跨进程恢复
- 可配 `DataSource` 或 JDBC 直连
- 同样支持压缩器

更重要的一个实现细节是：

- 每次写入都会先 `loadSnapshot()`
- 然后生成新 snapshot
- 最后 `replaceSnapshot(...)` 删除旧记录并整份重写

这说明 `JdbcAgentMemory` 当前更偏向正确性和一致语义，而不是高频增量写优化。对长会话、高 QPS 或超大 item 集合场景，需要自行评估数据库写放大成本。

## 8. 压缩机制如何工作

### 8.1 压缩触发时机

`InMemoryAgentMemory` 每次 `addUserInput`、`addOutputItems`、`addToolOutput` 后都会执行 `maybeCompress()`。

`JdbcAgentMemory` 也会在写入新 snapshot 前先调用 `applyCompressor(...)`。

这意味着压缩是同步写路径的一部分，不是后台异步任务。

### 8.2 `WindowedMemoryCompressor`

内置窗口压缩器的行为非常直接：

- 如果 item 数量未超过 `maxItems`，不处理
- 如果超过，只保留最后 `maxItems` 条
- `summary` 原样保留

它适合：

- 短任务
- 成本敏感场景
- 不要求长期语义总结

### 8.3 更稳的工程做法

生产环境更常见的方案通常不是“纯窗口”，而是：

- 旧上下文进入摘要
- 最近窗口保留原始 item
- 特定工具输出按类型做选择性保留

这也是 `MemorySnapshot(items, summary)` 这种设计存在的意义。

## 9. 典型用法

### 9.1 默认内存会话

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .build();
```

如果没有显式传 `memorySupplier(...)`，Builder 默认使用 `InMemoryAgentMemory::new`。

### 9.2 使用窗口压缩

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .memorySupplier(() -> new InMemoryAgentMemory(
                new WindowedMemoryCompressor(20)
        ))
        .build();
```

### 9.3 使用 JDBC 持久化 memory

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .memorySupplier(() -> new JdbcAgentMemory(
                JdbcAgentMemoryConfig.builder()
                        .dataSource(dataSource)
                        .sessionId("agent-session-001")
                        .compressor(new WindowedMemoryCompressor(20))
                        .build()
        ))
        .build();
```

### 9.4 打开独立 session

```java
AgentSession sessionA = agent.newSession();
AgentSession sessionB = agent.newSession();
```

前提是你的 `memorySupplier` 真正返回独立 memory，否则这两个 session 只是在 API 层分开，状态仍可能共享。

## 10. 扩展点

### 10.1 自定义 `AgentMemory`

适合：

- Redis 持久化
- 统一会话平台
- 分布式缓存
- 特定租户隔离方案

### 10.2 自定义 `MemoryCompressor`

适合：

- 摘要 + 窗口
- 分阶段压缩
- 按工具类型裁剪结果
- 长任务恢复优化

### 10.3 自定义 session 标识策略

对持久化 memory 来说，`sessionId` 决定恢复与隔离边界。它通常应该和业务会话 ID、工单 ID、任务 ID 或用户会话主键对齐。

## 11. 边界、限制与失败语义

### 11.1 `AgentMemory` 不等于全部 runtime state

`AgentMemory` 保存的是可回灌给模型的上下文状态。step 计数、线程状态、临时执行控制等运行时元数据，并不都存放在 `AgentMemory` 中。

### 11.2 `AgentMemory` 不等于 `ChatMemory`

两者边界应该明确区分：

- `ChatMemory` 解决基础多轮会话上下文
- `AgentMemory` 解决 Agent loop 内的输入、输出、工具结果与压缩语义

### 11.3 `AgentMemory` 也不等于 `CodingSession`

`Coding Agent` 的 session 还包含：

- 进程状态
- 文件系统操作历史
- checkpoint / compact 元数据
- 宿主事件账本

所以 `CodingSession` 比 `AgentMemory` 更宽。

### 11.4 压缩失败会直接影响写入

由于压缩发生在同步写路径上，自定义 `MemoryCompressor` 一旦抛异常，就会直接让当前写入失败。压缩器必须被视为 runtime 关键路径代码，而不是可随意失败的旁路插件。

### 11.5 `callId == null` 时不会写入工具结果

`addToolOutput(callId, output)` 的两个默认实现都要求 `callId` 非空；否则直接忽略。正常情况下 runtime 已经会在 tool call 归一化阶段补齐 `callId`，但自定义链路仍应保持这一约束。

## 12. 和相邻页面的关系

这页回答的是：

- Agent runtime 如何保存和恢复状态
- memory 在 loop 中如何读写
- session 隔离语义是什么

更细的实现和策略，请继续看：

1. [Memory 管理与压缩策略](/docs/agent/memory-management)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Agent Architecture](/docs/agent/architecture)
4. [Trace Observability](/docs/agent/trace-observability)
