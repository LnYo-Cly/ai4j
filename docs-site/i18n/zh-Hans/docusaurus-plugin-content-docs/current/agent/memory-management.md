---
sidebar_position: 6
---

# Memory 记忆管理与压缩策略

`Memory` 不是某个单独产品的小功能，而是 AI 系统里的通用基础能力。

在 AI4J 里：

- `ai4j` 核心层现在已经提供基础 `ChatMemory`
- `Agent` 直接建立在 `AgentMemory` 之上
- `Coding Agent` 的 session memory、compact、resume 也复用了这套基础能力
- `IChatService` / `IResponsesService` 仍然不会自动替你维护上下文，但你现在可以显式配合 `ChatMemory` 使用

实现层面现在有：

- 基础层：`InMemoryChatMemory`、`JdbcChatMemory`
- Agent 层：`InMemoryAgentMemory`、`JdbcAgentMemory`

所以这页虽然放在 `Agent` 专题，但概念上同样适用于 `Coding Agent` 和更一般的 AI runtime 设计。

换句话说：

- 如果你只是直接调用 `ChatCompletion` / `ResponseRequest`，可以自己维护历史，也可以直接用核心层 `ChatMemory`
- 如果你进入 `Agent` / `Coding Agent runtime`，memory 才是默认内建的一等能力

一眼区分两者：

- `ChatMemory`：重点是“多轮对话上下文”
- `AgentMemory`：重点是“模型输出、工具结果、控制消息如何在每一轮循环里继续参与推理”

如果你只需要基础会话上下文，而不是完整 Agent runtime，优先阅读：

- [ChatMemory：基础会话上下文](/docs/ai-basics/chat/chat-memory)

## 1. 为什么 Agent 一定要有 Memory

没有记忆，Agent 每一轮都会“失忆”，典型问题：

- 工具调用结果无法被下一轮模型看到
- 多轮任务无法累积上下文
- CodeAct 的 `CODE_RESULT/CODE_ERROR` 无法闭环

AI4J 的设计是：**Runtime 不直接拼历史字符串，而是把会话状态交给 `AgentMemory` 管理**。

## 2. 当前内存模型（基于 `AgentMemory` 接口）

核心结构：

- `items: List<Object>`
- `summary: String`（可选）
- `compressor: MemoryCompressor`（可选）

写入接口：

- `addUserInput(Object input)`
- `addOutputItems(List<Object> items)`
- `addToolOutput(String callId, String output)`

读取接口：

- `getItems()`
- `getSummary()`
- `clear()`

默认实现仍然是 `InMemoryAgentMemory`。

如果你希望 Agent 会话直接落到关系库，可以使用：

- `JdbcAgentMemory`
- `JdbcAgentMemoryConfig`

## 3. Memory item 的真实形态

AI4J 通过 `AgentInputItem` 生成统一结构：

- 用户消息：`type=message, role=user`
- 系统消息：`type=message, role=system`
- 工具返回：`type=function_call_output, call_id=..., output=...`

这意味着：

- ReAct 工具调用结果可被模型下一轮直接消费
- CodeAct 执行结果也会以 system message 回写 memory

## 4. Runtime 与 Memory 的交互时机

以 `BaseAgentRuntime` 为例：

1. `run` 开始：`memory.addUserInput(request.input)`
2. 模型返回后：`memory.addOutputItems(modelResult.memoryItems)`
3. 每次工具执行后：`memory.addToolOutput(callId, output)`
4. 下一轮 `buildPrompt`：`items = memory.getItems()`

所以 memory 是每步循环都参与的“状态源”。

---

## 4.1 Coding Agent 为什么也依赖这套 Memory

`Coding Agent` 虽然在产品形态上更像 CLI/TUI/ACP 工具，但底层会话状态并不是另外发明了一套完全不同的内存模型。

从源码和测试可以直接看出，它仍然依赖：

- `MemorySnapshot`
- `CodingSessionState.memorySnapshot`
- `CodingSessionCompactor`

这就是为什么 `Coding Agent` 会有：

- session save / resume / fork
- compact
- memory item count

也就是：

- `Agent` 侧强调推理循环中的 memory
- `Coding Agent` 侧强调持续会话中的 session memory

但两者底层是打通的。

## 5. 会话隔离语义（很关键）

`Agent.newSession()` 会创建新的 `AgentSession`，并给它独立 memory：

- 默认：`InMemoryAgentMemory::new`
- 如果你传了 `memorySupplier`，每次 session 用你自定义 memory

这保证：

- 不同用户会话不会串上下文
- 并发场景下状态隔离更安全

## 6. 压缩机制：`MemoryCompressor`

`InMemoryAgentMemory` 每次写入后都会 `maybeCompress()`：

- 如果配置了 compressor，就执行 `compress(MemorySnapshot)`
- 返回新的 `items + summary`

接口：

```java
public interface MemoryCompressor {
    MemorySnapshot compress(MemorySnapshot snapshot);
}
```

## 6.1 内置窗口压缩：`WindowedMemoryCompressor`

作用：仅保留最近 N 条 item。

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .memorySupplier(() -> new InMemoryAgentMemory(new WindowedMemoryCompressor(20)))
        .build();
```

适用场景：

- 短任务
- 高并发低成本
- 不要求长期语义记忆

## 7. 推荐压缩策略（实践）

## 策略 A：纯窗口

- 成本低
- 可能丢关键长期信息

## 策略 B：窗口 + 摘要（推荐）

- 旧对话压成 summary
- 最近 N 轮保留原文

## 策略 C：按任务分段记忆

- 每个子任务独立记忆池
- 汇总阶段只读子任务摘要

## 8. 自定义 Compressor 示例（摘要 + 窗口）

```java
public class HybridMemoryCompressor implements MemoryCompressor {

    private final int maxItems;

    public HybridMemoryCompressor(int maxItems) {
        this.maxItems = maxItems;
    }

    @Override
    public MemorySnapshot compress(MemorySnapshot snapshot) {
        List<Object> items = snapshot.getItems();
        if (items == null || items.size() <= maxItems) {
            return snapshot;
        }

        int split = items.size() - maxItems;
        List<Object> head = new ArrayList<>(items.subList(0, split));
        List<Object> tail = new ArrayList<>(items.subList(split, items.size()));

        String previousSummary = snapshot.getSummary() == null ? "" : snapshot.getSummary();
        String newSummary = previousSummary + "\n[压缩] 历史片段条数=" + head.size();

        return MemorySnapshot.from(tail, newSummary.trim());
    }
}
```

> 你可以把 `head` 交给模型生成更高质量摘要，这样 summary 语义更强。

## 9. 官方 JDBC 持久化与自定义扩展

如果你希望先落 MySQL / PostgreSQL / H2，而不是自己从零写一版 `AgentMemory`，可以先直接用官方 JDBC 实现：

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .memorySupplier(() -> new JdbcAgentMemory(
                JdbcAgentMemoryConfig.builder()
                        .jdbcUrl("jdbc:mysql://localhost:3306/ai4j")
                        .username("root")
                        .password("123456")
                        .sessionId("agent-session-001")
                        .compressor(new WindowedMemoryCompressor(20))
                        .build()
        ))
        .build();
```

如果你是 Spring / 连接池场景，也可以直接传 `DataSource`。

例如 Spring Boot + MySQL：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai4j?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
```

```java
.memorySupplier(() -> new JdbcAgentMemory(
        JdbcAgentMemoryConfig.builder()
                .dataSource(dataSource)
                .sessionId("agent-session-001")
                .compressor(new WindowedMemoryCompressor(20))
                .build()
))
```

这层官方实现解决的是：

- 跨进程保存 Agent items / summary
- 同一 session 的恢复
- 保持和 `InMemoryAgentMemory` 一致的读写语义

如果你要 Redis、分布式缓存、分库分表或统一会话平台，再考虑继续自定义。

### 9.1 自定义持久化 Memory（例如 Redis）

如果你希望会话跨进程/跨实例保留，可实现 `AgentMemory`：

```java
public class RedisAgentMemory implements AgentMemory {
    @Override
    public void addUserInput(Object input) {
        // 写 Redis
    }

    @Override
    public void addOutputItems(List<Object> items) {
        // 写 Redis
    }

    @Override
    public void addToolOutput(String callId, String output) {
        // 写 Redis
    }

    @Override
    public List<Object> getItems() {
        return new ArrayList<>();
    }

    @Override
    public String getSummary() {
        return null;
    }

    @Override
    public void clear() {
        // 清理会话数据
    }
}
```

接入：

```java
.memorySupplier(() -> new RedisAgentMemory())
```

## 10. 与 Trace 的关系

当前 trace 会记录模型输入输出、工具参数输出；memory 压缩事件类型虽在 `AgentEventType` 中预留了 `MEMORY_COMPRESS`，但默认 runtime 还未主动发布该事件。

建议：

- 在自定义 memory/compressor 内主动打印或上报压缩指标
- 记录 `before_items/after_items/summary_length`

---

## 10.1 与 Skill、Tool、MCP 的关系

这几个能力经常一起出现，但职责不同：

- `Memory`：保存已经发生过的上下文
- `Skill`：提供遇到某类任务时的可复用方法说明
- `Tool`：执行具体动作
- `MCP`：把外部工具系统挂进来

可以把它们理解成：

- `Memory` 负责“记住”
- `Skill` 负责“知道怎么做”
- `Tool/MCP` 负责“真的去做”

## 11. Memory 配置建议矩阵

- FAQ/客服：窗口 20~40，低成本优先
- 工单处理：窗口 + 摘要，保留关键动作链
- 研究任务：更大窗口 + 摘要 + 子任务拆分
- CodeAct：建议保留最近几轮 code/result，避免修复上下文丢失

## 12. 常见问题

1. 输出突然变差：通常是压缩过猛，关键上下文被裁掉。
2. 会话串数据：检查是否复用了同一个 session 或共享 memory 实例。
3. token 成本高：先看 items 长度，再考虑窗口压缩和摘要策略。

## 13. 关联源码与测试

- `JdbcAgentMemory`
- `InMemoryAgentMemory`
- `MemoryCompressor`
- `WindowedMemoryCompressor`
- `MemorySnapshot`
- `Agent.newSession()` / `AgentSession`

结合 `CodeActRuntimeTest`、`StateGraphWorkflowTest` 观察多步场景下的记忆效果会更直观。
