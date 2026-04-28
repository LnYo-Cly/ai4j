# Memory and State

在 `ai4j-agent` 里，memory 不是“聊天记录附件”，而是 Agent loop 的状态源。

只要进入多步推理，runtime 每一轮都要回答四个问题：

- 当前轮之前的上下文从哪里取
- 模型输出的哪些内容要进入下一轮
- 工具结果如何回灌给模型
- 长会话如何在不丢关键信息的前提下裁剪

这套语义统一落在 `AgentMemory` 上。理解 memory，才能真正看清 ReAct、CodeAct、Team runtime 为什么能持续运行，而不是“一次请求 + 一次响应”的聊天封装。

## 1. 状态模型先看最短定义

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/AgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/InMemoryAgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/JdbcAgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`

`AgentMemory` 接口非常克制：

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

它只建模三类写入：

- 用户输入
- 模型输出 items
- 工具输出

这说明 memory 不是给 UI 做 transcript 展示的二级副本，而是 Agent 下一轮 prompt 的直接来源。

## 2. 真实执行链：状态如何进入下一轮

真正的主链在 `BaseAgentRuntime.runInternal()`：

```text
AgentRequest.input
  -> memory.addUserInput(...)
  -> buildPrompt(items = memory.getItems())
  -> modelClient.create(...)
  -> memory.addOutputItems(modelResult.getMemoryItems())
  -> toolExecutor.execute(...)
  -> memory.addToolOutput(callId, output)
  -> buildPrompt(items = memory.getItems())
```

这里最关键的结论有两个：

- runtime 不维护一份独立的“隐藏历史”，下一轮上下文总是重新从 `memory.getItems()` 取回
- 工具结果和模型输出不是旁路数据，而是正式进入下一轮状态

因此，memory 的行为会直接改变 Agent 的推理轨迹，而不只是影响“历史是否可查看”。

## 3. `AgentMemory` 解决的不是存储，而是状态统一

如果只维护一个字符串 prompt，会很快遇到三个问题：

- 用户输入、模型输出、工具输出没有统一数据面
- 压缩时只能裁整段文本，无法保留结构化 item
- 不同 runtime 很难共享一套上下文语义

`ai4j-agent` 选择按 `List<Object>` 保留 item，而不是预先拼成大文本。这样做有三个直接收益：

- `BaseAgentRuntime.buildPrompt()` 可以统一构造 `AgentPrompt`
- 工具输出可以以 `function_call_output` 语义重回模型
- 压缩器可以在 item 级别裁剪，而不是在最终 prompt 文本上做不可逆处理

## 4. `summary` 的地位比“摘要字段”更高

很多项目会把 summary 当成 metadata，但 `ai4j-agent` 的做法更激进。

无论是 `InMemoryAgentMemory.getItems()` 还是 `JdbcAgentMemory.getItems()`，只要 `summary` 非空，都会先插入一条：

```java
AgentInputItem.systemMessage(summary)
```

然后再追加原始 items。

这意味着：

- summary 会进入模型上下文，不只是给宿主应用看
- summary 的提示强度接近一条 system-level memory item
- 如果摘要写坏，后续所有轮次都可能被它持续带偏

这也是为什么 memory 压缩不是“可有可无的小优化”，而是影响推理质量的核心路径。

## 5. `InMemoryAgentMemory`：默认实现的真实语义

`AgentBuilder.build()` 默认使用：

```java
Supplier<AgentMemory> resolvedMemorySupplier =
        memorySupplier == null ? InMemoryAgentMemory::new : memorySupplier;
```

也就是说，不显式配置时，每个 Agent 会拿到一份进程内 memory。

### 5.1 写入行为

`InMemoryAgentMemory` 的三类写入都很直接：

- `addUserInput(String)` 会包装成 `AgentInputItem.userMessage(...)`
- `addOutputItems(...)` 直接追加模型返回的 memory items
- `addToolOutput(callId, output)` 会包装成 `AgentInputItem.functionCallOutput(...)`

其中有一个重要边界：

- `callId == null` 时，工具输出会被直接忽略

正常情况下 runtime 的 `normalizeToolCalls()` 会补齐缺失的 `callId`，默认格式是 `tool_step_<step>_<index>`，但如果你绕过 runtime 自己写链路，这个约束必须自己保证。

### 5.2 压缩触发时机

`InMemoryAgentMemory` 每次写入后都会同步调用 `maybeCompress()`。

这意味着：

- 压缩发生在写路径上
- 压缩异常会直接影响当前请求
- 压缩器必须被当成关键路径代码来写

一个容易忽略的细节是：

- `setCompressor(...)` 只替换压缩器引用，不会立刻重压现有状态

也就是说，内存实现只有在下一次 `addUserInput`、`addOutputItems`、`addToolOutput` 时，新的压缩器才真正生效。

### 5.3 `snapshot()/restore()` 只是实现级能力

`InMemoryAgentMemory` 暴露了 `snapshot()` 和 `restore()`，但它们不在 `AgentMemory` 接口里。

这说明 runtime 层只依赖统一的读写契约，而“快照导出/恢复”是具体存储实现附加出来的工程能力，不是所有 memory backend 都被强制要求支持的公共 API。

## 6. `JdbcAgentMemory`：它提供恢复能力，也带来写放大

`JdbcAgentMemory` 不是在 `InMemoryAgentMemory` 上简单套一层持久化，而是重新定义了一套明确的持久化边界。

### 6.1 构造约束

构造时会做几类强校验：

- `sessionId` 不能为空
- `tableName` 必须通过 SQL identifier 正则校验
- `dataSource` 和 `jdbcUrl` 至少提供一个

如果 `initializeSchema = true`，构造阶段还会自动尝试建表。

### 6.2 读写模型

它的读写模式不是 append-only，而是：

1. `loadSnapshot()`
2. 在内存里修改 snapshot
3. `replaceSnapshot(...)`

`replaceSnapshot(...)` 的实现是：

- 先按 `session_id` 删除旧记录
- 再重新写入 summary 记录
- 再批量写入 item 记录

这代表 `JdbcAgentMemory` 当前的设计优先级是：

- 保持和内存实现一致的语义
- 保持恢复路径简单
- 允许 summary 和 items 一起原子替换

而不是：

- 最小增量写
- 极限高频吞吐

对长会话、高 QPS 或大体量工具输出场景，这个写放大成本要单独评估。

### 6.3 `setCompressor(...)` 的行为和内存实现不同

`JdbcAgentMemory.setCompressor(...)` 会立刻执行：

```java
replaceSnapshot(applyCompressor(loadSnapshot()))
```

也就是说，JDBC 实现切换压缩器时，会当场重写当前持久化状态；内存实现则不会。这是两个实现最容易被误判的差异之一。

### 6.4 summary 在数据库里的存储方式

JDBC 实现把 snapshot 拆成两类 entry：

- `entry_type = item`
- `entry_type = summary`

summary 不是冗余字符串拼在 item 里，而是单独存一条记录；但在 `getItems()` 阶段，它仍然会重新转成 `systemMessage(summary)` 注入上下文。

## 7. 压缩器契约：压缩的是 snapshot，不是单条消息

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/MemoryCompressor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/MemorySnapshot.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/WindowedMemoryCompressor.java`

压缩器处理的是：

- `items`
- `summary`

而不是“单条消息进来时怎么裁一刀”。这给了实现者足够的自由度去做：

- 保留最近窗口
- 把旧上下文折叠进 summary
- 对某些工具输出保留原文，对另一些工具输出只留摘要

### 7.1 内置 `WindowedMemoryCompressor`

默认内置窗口压缩器只做一件事：

- 超过 `maxItems` 时，仅保留最后 N 条 item

它不会自动生成摘要，也不会修改已有 summary。

所以它更像“硬裁窗口”，而不是“长期记忆策略”。如果你的 Agent 需要跨多轮保持结论、约束、先前工具发现，单纯的窗口压缩通常不够。

## 8. Session 隔离并不是 runtime 复制，而是 memory 复制

`Agent.newSession()` 的关键动作不是克隆 runtime，而是从 `memorySupplier` 再取一份 memory。

这会带来一个非常重要的判断标准：

- session 是否隔离，不取决于 `AgentSession` 这个类名
- 取决于 `memorySupplier` 每次是否真的返回新的状态容器

如果你把 `memorySupplier` 写成共享单例，即使表面上拿到了两个 session，它们仍会串状态。

对 `JdbcAgentMemory` 来说，还要额外满足：

- 不能复用同一个 `sessionId`

否则即使对象实例是新的，也仍然会落到同一份持久化快照上。

## 9. memory 的边界：它不是所有运行态

为了避免误用，需要把 memory 和其他概念分开。

### 9.1 它不是全部 runtime state

`AgentMemory` 只负责可回灌给模型的上下文状态。step 计数、事件流、线程执行状态、工具线程池之类的运行期信息，并不都存在这里。

### 9.2 它不是 `ChatMemory`

`ChatMemory` 更偏普通多轮对话上下文；`AgentMemory` 面向的是 Agent loop 内部的输入、模型输出、工具输出和压缩策略。

### 9.3 它也不是 Coding runtime 的完整 session

`ai4j-coding` 里的 session 还会包含进程、文件系统、checkpoint、compact 等宿主态信息。它比 `AgentMemory` 更宽，不能混为一谈。

## 10. 自定义实现时真正要守住的约束

如果你要自己实现 Redis、MongoDB 或业务侧会话存储，最低限度要守住下面这些语义：

- `getItems()` 必须返回可直接参与下一轮 prompt 的 items
- `addToolOutput()` 应把工具结果转换成模型能理解的 item 语义
- `summary` 和 recent items 不能互相覆盖成不可恢复的单层文本
- 压缩必须在错误语义上保持可预期，不能静默吞掉状态
- session 隔离边界必须和你的业务会话 ID 保持一致

如果其中任意一条做偏，表面上 Agent 还能跑，但长期行为会开始漂移。

## 11. 调试这块时先看哪几个点

出现“模型忘事”“工具结果没回灌”“多 session 串状态”“长任务越跑越歪”时，先查下面几个入口：

- `BaseAgentRuntime.runInternal()` 是否真的调用了 `memory.addUserInput / addOutputItems / addToolOutput`
- `memory.getItems()` 返回的列表里，summary 是否被注入成了 `systemMessage`
- 自定义 `memorySupplier` 是否复用了单例
- `JdbcAgentMemory` 的 `sessionId` 是否被错误复用
- 压缩器是否把关键 item 直接裁掉而没有进 summary

这些地方比看前端聊天记录更接近真实问题源头。

## 12. 继续阅读

1. [Memory 管理与压缩策略](/docs/agent/memory-management)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Agent Architecture](/docs/agent/architecture)
4. [Trace Observability](/docs/agent/trace-observability)
