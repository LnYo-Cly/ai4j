---
sidebar_position: 6
---

# Memory Management

[Memory and State](/docs/agent/memory-and-state) 解释的是 `AgentMemory` 在 runtime 中的角色；这页进一步讨论工程层面的记忆管理问题：

- 什么东西会被压缩
- `summary` 怎样重新进入模型上下文
- 短期窗口和长期摘要如何组合
- `InMemory` 与 `JDBC` 的真实成本差异是什么
- 什么时候该自定义 `MemoryCompressor` 或 `AgentMemory`

## 1. 记忆管理真正要解决的不是“存下来”

只把上下文保存下来，通常并不能解决长任务问题。真正的工程难点在于：

- 历史越长，token 成本越高
- 工具结果往往比普通对话更膨胀
- 旧上下文既不能全丢，也不能全保留
- 同一 Agent 可能要支持短任务与长任务两种形态

因此 memory 管理的目标不是“保留最多”，而是“以可接受成本保留最有价值的上下文”。

## 2. 当前实现的状态结构

无论 `InMemoryAgentMemory` 还是 `JdbcAgentMemory`，其核心状态都可以看成：

- `items`
- `summary`
- `compressor`

对应源码：

- `memory/InMemoryAgentMemory`
- `memory/JdbcAgentMemory`
- `memory/MemorySnapshot`
- `memory/MemoryCompressor`

其中：

- `items` 保存当前窗口内的原始上下文
- `summary` 保存压缩后的长期语义
- `compressor` 负责把 snapshot 变成新的 snapshot

## 3. `summary` 的语义非常重要

在当前实现中，`summary` 不是旁路元数据，而是会在 `getItems()` 时重新插入上下文。

实现方式是：

- 如果 `summary` 为空，直接返回 `items`
- 如果 `summary` 不为空，先插入一条 `AgentInputItem.systemMessage(summary)`，再返回其余 items

这意味着：

- 摘要会重新进入模型输入
- 摘要质量会直接影响后续推理
- 摘要不是仅供存档或 UI 展示的备注字段

从工程角度看，这一设计支持“历史摘要 + 最近窗口”这种常见模式，但也要求压缩器对 `summary` 的措辞和信息密度更谨慎。

## 4. 压缩触发时机

压缩不是后台懒处理，而是在写路径同步触发。

### 4.1 `InMemoryAgentMemory`

每次发生下面任一写入时都会尝试压缩：

- `addUserInput(...)`
- `addOutputItems(...)`
- `addToolOutput(...)`

写入后调用 `maybeCompress()`，再更新 `items` 与 `summary`。

### 4.2 `JdbcAgentMemory`

JDBC 实现也会在生成新 snapshot 后，先走 `applyCompressor(...)`，再把新 snapshot 持久化。

这意味着：

- 压缩逻辑处于同步关键路径
- 压缩器异常会直接影响当前写入
- 压缩策略不能当成“随便失败也无所谓”的插件

## 5. 内置压缩策略：`WindowedMemoryCompressor`

源码：

- `memory/WindowedMemoryCompressor`

它的语义非常直接：

- 如果 item 数未超过 `maxItems`，不压缩
- 如果超过，只保留最后 `maxItems` 条
- `summary` 原样保留

这意味着它本质上是“窗口裁剪器”，不是“语义摘要器”。

### 5.1 它适合什么场景

- 短任务
- 调试期
- 成本敏感任务
- 最近上下文最重要的任务

### 5.2 它不擅长什么

- 长期语义保留
- 对工具结果做选择性压缩
- 保留跨阶段目标、约束、结论

如果会话价值集中在长期计划和关键结论，而不是最近几轮原文，纯窗口通常不够。

## 6. 常见压缩策略

### 6.1 纯窗口

特点：

- 实现最简单
- 成本最低
- 容易丢长期关键信息

适合：

- FAQ
- 短客服会话
- 工具调用较少的轻任务

### 6.2 摘要 + 窗口

特点：

- 历史内容进入 `summary`
- 最近 N 条保留原始 items
- 兼顾长期语义和局部细节

适合：

- 大多数业务 Agent
- 长于普通聊天、短于研究任务的多步任务

### 6.3 分阶段压缩

特点：

- 按任务阶段整理上下文
- 阶段完成后只保留阶段结论
- 让后续轮次不再背负全部原始过程

适合：

- 研究型任务
- 多阶段工作流
- CodeAct 较长执行链

### 6.4 按工具类型选择性保留

特点：

- 对高噪声工具结果做强裁剪
- 对低频但关键的工具输出做保留

适合：

- 工具输出体积差异很大的系统
- 包含检索、文件读写、代码执行、网络抓取等异构工具的系统

## 7. 自定义 `MemoryCompressor` 的设计建议

`MemoryCompressor` 的输入输出都是 `MemorySnapshot`。最稳妥的设计思路是：

1. 先判断是否真的需要压缩
2. 明确哪些 item 应进入摘要
3. 明确哪些 item 必须保留原文
4. 明确 `summary` 是否累加、覆盖或分段更新

一个实用原则是：

> 摘要保留决策与结论，窗口保留最近可执行细节。

### 7.1 一个更合理的摘要方向

对于工具驱动任务，摘要通常至少应保留：

- 当前目标
- 已完成的关键步骤
- 关键工具输出结论
- 尚未解决的问题
- 后续下一步约束

如果只记录“压缩了多少条”，对后续推理帮助有限。

## 8. `InMemory` 与 `JDBC` 的真实差异

### 8.1 `InMemoryAgentMemory`

优点：

- 最轻量
- 延迟低
- 适合本地开发与单进程任务

限制：

- 进程退出即丢失
- 无法天然跨实例恢复

### 8.2 `JdbcAgentMemory`

优点：

- 状态可持久化
- 可通过 `sessionId` 恢复会话
- 可以接入现有数据库与连接池

限制也要写清楚：

- 每次写入都要先读 snapshot
- 然后删除旧记录并整份重写新 snapshot
- 更偏向语义一致性，而非高频增量写优化

这意味着在超长会话、高频写入或高并发环境下，JDBC 路线需要评估：

- 写放大
- 锁争用
- 大文本存储成本
- 数据库吞吐瓶颈

## 9. `sessionId` 才是持久化会话隔离的真正边界

使用 `JdbcAgentMemory` 时，是否真正隔离会话，取决于 `sessionId`，而不只是实例是否新建。

如果两个 session 指向相同 `sessionId`，它们读写的就是同一份持久化状态。

因此 `sessionId` 通常应与下面某一种稳定业务标识对齐：

- 用户会话 ID
- 工单 ID
- 任务 ID
- 对话线程 ID

## 10. 与 `Agent.newSession()` 的关系

`Agent.newSession()` 的语义是：

- 复用原有 runtime 和配置
- 为新 session 换一份 memory

如果 `memorySupplier` 返回的是：

- 新的 `InMemoryAgentMemory` 实例，则状态天然隔离
- 新的 `JdbcAgentMemory` 实例但共用同一 `sessionId`，则状态仍不隔离

所以 session 隔离最终要同时看：

- memory 实例是否独立
- 持久化 key 是否独立

## 11. 与 Trace 的关系

`AgentEventType` 中包含 `MEMORY_COMPRESS`，Trace 体系也能消费这一类事件，但默认 memory 实现并不会主动发出压缩事件。

这意味着如果你想观测记忆管理质量，通常需要在自定义 memory 或 compressor 中主动记录：

- 压缩前 item 数
- 压缩后 item 数
- summary 长度
- 被裁剪的工具结果规模

否则“为什么这轮回答开始变差”很难定位到 memory 策略。

## 12. 实用选型建议

| 场景 | 推荐策略 |
| --- | --- |
| 本地开发、短任务 | `InMemoryAgentMemory` + 纯窗口 |
| 标准业务 Agent | `InMemory` 或 `JDBC` + 摘要 + 窗口 |
| 研究型或长任务 | 摘要 + 窗口 + 分阶段压缩 |
| 需要跨进程恢复 | `JdbcAgentMemory` 或自定义持久化 memory |
| 工具输出极大 | 选择性保留关键工具结果 |

## 13. 典型接入方式

### 13.1 简单窗口压缩

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .memorySupplier(() -> new InMemoryAgentMemory(
                new WindowedMemoryCompressor(20)
        ))
        .build();
```

### 13.2 JDBC 持久化

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

### 13.3 自定义压缩器

```java
public class HybridMemoryCompressor implements MemoryCompressor {

    private final int maxItems;

    public HybridMemoryCompressor(int maxItems) {
        this.maxItems = maxItems;
    }

    @Override
    public MemorySnapshot compress(MemorySnapshot snapshot) {
        if (snapshot == null || snapshot.getItems() == null || snapshot.getItems().size() <= maxItems) {
            return snapshot;
        }

        int split = snapshot.getItems().size() - maxItems;
        java.util.List<Object> history = new java.util.ArrayList<Object>(snapshot.getItems().subList(0, split));
        java.util.List<Object> recent = new java.util.ArrayList<Object>(snapshot.getItems().subList(split, snapshot.getItems().size()));

        String oldSummary = snapshot.getSummary() == null ? "" : snapshot.getSummary().trim();
        String newSummary = (oldSummary + "\n" + "Completed history items: " + history.size()).trim();

        return MemorySnapshot.from(recent, newSummary);
    }
}
```

这个例子只是说明接口用法。真正生产场景通常应该生成有语义的摘要，而不是只统计条数。

## 14. 继续阅读

1. [Memory and State](/docs/agent/memory-and-state)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Runtime Implementations](/docs/agent/runtime-implementations)
4. [Trace Observability](/docs/agent/trace-observability)
