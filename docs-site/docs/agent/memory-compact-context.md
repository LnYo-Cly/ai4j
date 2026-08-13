---
sidebar_position: 6
title: Memory Compact Context Projector
description: 介绍 ai4j-agent 的 ContextProjector 与 CompactPolicy：如何把 memory snapshot 投影成本轮 prompt、用结构化 CompactResult 压缩长程上下文，并让压缩过程可诊断、可恢复。
tags: [concept]
---

# Memory Compact Context Projector

这一页说明 `ai4j-agent` 里新加入的 Memory / Compact / Context Projector 基础能力。

先说结论：它不是“自动变聪明的摘要器”，而是把长程 Agent 里最容易混乱的三件事拆开：

```text
SessionEventLog = 完整事实历史
AgentMemory     = Agent loop 的状态源
ModelContext    = 本轮真正发给模型的上下文
```

P0-B 的价值是让这三层可以被保存、投影、压缩和诊断，而不是让开发者只能在一个越来越长的 `List<Object>` 上硬裁剪。

## 1. 为什么需要这一层

长程 Agent 会不断积累：

- 用户输入
- 模型输出
- 工具调用
- 工具结果
- 失败命令
- 测试结果
- 人工确认
- sandbox 状态
- 文件和 artifact 变化

如果所有东西都直接塞进模型上下文，会遇到三个问题：

1. 上下文窗口会溢出。
2. 旧信息和新信息没有优先级。
3. 压缩后丢了什么没人知道。

所以 P0-B 增加了两个明确边界：

- `ContextProjector`：决定本轮 prompt 带哪些 item。
- `CompactPolicy`：把 memory snapshot 压缩成结构化结果和新的 memory snapshot。

## 2. 新增核心类

| 类 | 包 | 职责 |
| --- | --- | --- |
| `ContextBudget` | `io.github.lnyocly.ai4j.agent.context` | 描述上下文预算，例如最大 item 数、近似字符数、保留前缀数量、Tier-1/2 分级开关 |
| `ContextProjector` | `io.github.lnyocly.ai4j.agent.context` | 把 memory items 投影成本轮 prompt items |
| `DefaultContextProjector` | `io.github.lnyocly.ai4j.agent.context` | 默认投影器：保留 pinned prefix 和 recent tail |
| `TypeAwareContextProjector` | `io.github.lnyocly.ai4j.agent.context` | 类型感知投影器：Tier-1 工具结果 microcompact + Tier-2 reasoning 裁剪 |
| `ContextProjection` | `io.github.lnyocly.ai4j.agent.context` | 投影后的 items 和报告 |
| `ContextReport` | `io.github.lnyocly.ai4j.agent.context` | 记录投影前后 item 数、近似字符数、drop 数和 notes |
| `CompactPolicy` | `io.github.lnyocly.ai4j.agent.compact` | 压缩策略接口 |
| `CompactResult` | `io.github.lnyocly.ai4j.agent.compact` | 结构化 compact 结果 |
| `StructuredSummaryCompactPolicy` | `io.github.lnyocly.ai4j.agent.compact` | 内置确定性结构摘要策略 |
| `LlmCompactPolicy` | `io.github.lnyocly.ai4j.agent.compact` | Tier-3：LLM 结构化摘要，回填 `CompactResult` 字段 |
| `CompactPolicyMemoryCompressor` | `io.github.lnyocly.ai4j.agent.compact` | 把 `CompactPolicy` 适配成已有 `MemoryCompressor` |

## 2.5 三层分级压缩：投影 vs 压缩

上面两个边界（`ContextProjector` / `CompactPolicy`）合起来是**三层分级**，代价和破坏性逐层升高：

| 层 | 落在哪 | 触发 | 依赖 LLM | 改 memory？ |
| --- | --- | --- | --- | --- |
| **Tier-1** 工具结果 microcompact | `TypeAwareContextProjector` | 每轮 | 否 | **否**（只改投影） |
| **Tier-2** reasoning 裁剪 | `TypeAwareContextProjector` | 每轮（需开关） | 否 | **否**（只改投影） |
| **Tier-3** LLM 摘要 | `LlmCompactPolicy` | 阈值触发 | 是 | **是**（`memory.restore`） |

关键区别，也是这套设计的核心：

```text
Tier-1/2 = 投影层。memory 里事实还在，只是这一轮模型看不到。
Tier-3   = 压缩层。memory 被 summary 替换，旧 item 真的没了。
```

**所以 Tier-1 可以很激进。** 裁掉的工具结果不是删除——原文还躺在 memory 里，Tier-3 摘要时仍取得到完整内容，replay 和审计也不受影响。这一点和 Claude Code 不同：那边 microcompact 是就地改消息数组，裁了就是裁了。

### Tier-1：只留最近 N 条工具结果

工具结果是最占地方的东西：读一个大文件、跑一次测试，几千字符进 memory，但三轮之后模型基本不会再回头看。

```java
Agent agent = Agents.react()
    .modelClient(modelClient)
    .model("gpt-4.1")
    .contextProjector(new TypeAwareContextProjector())
    .contextBudget(ContextBudget.builder()
        .maxItems(40)
        .maxRecentToolResults(5)   // 只有最近 5 条工具结果保留原文
        .build())
    .build();
```

更早的 `function_call_output` 会被替换成占位符，而不是直接抽走：

```text
{type: function_call_output, call_id: call-3, output: "[tool result cleared: read(call-3)]"}
```

留占位符是刻意的——模型需要知道"这里调过 read，结果现在不在了"，而不是看到一段凭空消失的历史。工具名靠 `call_id` join 回 assistant 消息的 `tool_calls[].id` 拿到（`function_call_output` 本身只有 `call_id`，没有工具名）。

`maxRecentToolResults` 不配（默认 `null`）时 Tier-1 不启用，`TypeAwareContextProjector` 的行为与 `DefaultContextProjector` **完全一致**——这是有回归测试守住的向后兼容契约。

### Tier-2：清掉旧轮次的 reasoning

```java
ContextBudget.builder()
    .maxRecentToolResults(5)
    .trimOldReasoning(true)     // 默认 false
    .build();
```

只清**最后一条 user 消息之前**的 `reasoning` item——当前轮的推理链保留，因为模型可能还在上面继续。item 的 `type` 和 `id` 保留，只清 payload，序列结构不破。

:::note 只对 Responses API 形态生效
`reasoning` 作为独立 memory item 存在，是 OpenAI Responses API 的形态。Anthropic / OpenAI Chat 路径下 reasoning 走 `AgentModelResult.reasoningText`，本来就不进 memory，Tier-2 对它们是空操作。
:::

### 哪一层生效：看 ContextReport.notes

三层都发 `MEMORY_COMPRESS` 事件，靠 `notes` 区分：

```text
tier1 microcompact: cleared 3 old tool result(s), kept recent 5
tier2 reasoning-trim: cleared 2 reasoning item(s)
maxItems applied: 40
```

### 和 Claude Code 的对照

| Claude Code | AI4J | 差异 |
| --- | --- | --- |
| Tier-1 microcompact（每轮清旧工具结果） | `TypeAwareContextProjector` + `maxRecentToolResults` | AI4J 走投影不改 memory；keep-N 可配，非硬编码 |
| Tier-2 类型感知裁剪 | `trimOldReasoning` | Claude Code 该层部分是 API 服务端行为，AI4J 作为 SDK 只做客户端可控的部分 |
| Tier-3 九段结构化摘要 | `LlmCompactPolicy` | AI4J 是七段（见下）；`sandboxState`/`userConfirmations` 属 coding-agent 场景，不在 core agent 层强求 |
| `cache_edits`（prompt cache 外科删除） | 不做 | 需要服务端协同，SDK 侧无对应物 |


## 3. Context Projector：控制本轮 prompt

默认投影器做一件简单但稳定的事：

```text
保留前 N 个 pinned prefix item
+
保留最近 tail item
+
返回 ContextReport
```

示例：

```java
Agent agent = Agents.react()
    .modelClient(modelClient)
    .model("gpt-4.1")
    .memorySupplier(InMemoryAgentMemory::new)
    .contextProjector(new DefaultContextProjector())
    .contextBudget(ContextBudget.builder()
        .maxItems(20)
        .pinnedPrefixItems(1)
        .build())
    .build();
```

如果 memory 中第一条是长期 summary 或系统级工作记忆，可以用 `pinnedPrefixItems(1)` 保住它；其余预算优先给最近上下文。

运行时会在 prompt 构造前调用 projector。当前 `ReActRuntime`、`DeepResearchRuntime` 和 `CodeActRuntime` 都会走这个投影入口。

## 4. Context Report：知道丢了什么

每次发生投影时，runtime 会发布 `AgentEventType.MEMORY_COMPRESS` 事件，payload 是 `ContextReport`。

`ContextReport` 包含：

- `sourceItemCount`
- `projectedItemCount`
- `droppedItemCount`
- `sourceApproxChars`
- `projectedApproxChars`
- `itemLimitApplied`
- `characterLimitApplied`
- `notes`

这让宿主、trace 或 session event log 可以知道：

```text
这轮模型看到的是完整 memory，还是被投影过的工作上下文？
投影后保留了多少？丢了多少？触发了哪种限制？
```

## 4.5 压缩的机制与时机：何时触发、压什么、失败了怎么办

这是 compact 最容易被误解的部分。三条规则定死：

### 时机：每一步开头、模型调用前检查，由 policy 决定要不要压

runtime 在 `BaseAgentRuntime` 的每一步**开头**（模型调用之前）调 `autoCompactIfNecessary`：

```text
每一步 step:
  → autoCompactIfNecessary（在本步模型调用之前）:
      policy = context.getCompactPolicy()
      if policy == null: 跳过
      snapshot = memory.snapshot()
      if !policy.shouldCompact(snapshot): 跳过          ← policy 决定时机
      → policy.compact(snapshot)
      → memory.restore(result.getMemory())              ← 压缩后写回 memory
  → executeModel(...)                                    ← 用（可能已压缩的）memory 构造 prompt
  → 工具执行（memory.addToolOutput ...）                  ← 事实进 memory
  → 下一步
```

关键：**runtime 不自己判断"该压了"，时机判断完全委托给 `CompactPolicy.shouldCompact(snapshot)`**。压缩发生在模型调用前，所以这一步的 prompt 用的是压缩后的 memory。

- 基础 `CompactPolicy` 的 `shouldCompact` 默认返回 `false` —— **不配 policy 就不会自动压缩**（纯手动）。
- `StructuredSummaryCompactPolicy` 覆写为 `snapshot.getItems().size() > maxItems` —— **item 数超阈值才压**。
- `LlmCompactPolicy` 的 `shouldCompact` 同样是计数阈值（`items.size() > maxItems`）；**模型只在 `compact()` 里生成摘要，不参与"要不要压"的决策**。它和 `StructuredSummaryCompactPolicy` 的时机判断完全一样，区别在压的时候用什么手段（LLM 摘要 vs 机械投影）。

所以"何时压缩"的答案不是固定的，而是**你选的 policy 决定**。三种典型策略：

| Policy | shouldCompact 判断 | 适合 |
| --- | --- | --- |
| 基础（默认） | 永不（手动才压） | 短任务，不需要自动 |
| `StructuredSummaryCompactPolicy` | item 数 > maxItems | 确定性的 item 计数阈值 |
| `LlmCompactPolicy` | item 数 > maxItems（同上） | 同样的计数阈值，但压缩时用 LLM 结构化摘要 |

### 压什么：压缩的是 memory snapshot，不是 prompt

`compact(MemorySnapshot)` 拿到的是 memory 的完整快照（items + summary），压缩后返回**新的 `MemorySnapshot`**，runtime 再 `memory.restore(...)` 写回。**不是改 prompt，也不是删单条消息**——是替换 memory 的事实集合，下一轮 `buildPrompt(memory.getItems())` 自然就小了。

这也解释了为什么压缩后旧的 item 细节会丢（被 summary 取代）——它们不再在 `memory.getItems()` 里了。

### 两条触发路径

1. **自动**（默认开发者的选择）：配一个带 `shouldCompact` 的 policy（如 `StructuredSummaryCompactPolicy`），runtime 每轮自动检查。
2. **手动**：任何时刻调 `session.compact(policy)`，立即压一次。适合 UI 上"用户点了压缩"或"任务阶段切换时主动收口"。

```java
// 自动：挂在 context 上，runtime 每轮检查
Agent agent = Agents.react()
        .modelClient(client)
        .compactPolicy(new StructuredSummaryCompactPolicy(
                ContextBudget.builder().maxItems(30).pinnedPrefixItems(1).build()))
        .build();

// 手动：显式压一次
session.compact(new StructuredSummaryCompactPolicy(budget));
```

### 失败安全：压缩异常不中断 run

`autoCompactIfNecessary` 把 `policy.compact(...)` 包在 try/catch 里：**压缩抛异常，run 继续用未压缩的 memory，不会被压缩故障拖垮**。这是刻意的——压缩是优化，不是关键路径，不能因为它失败而让整个 agent run 崩。

:::warning 手动 compact 不享受这个保护
自动路径(runtime 调)吞异常；但你直接调 `session.compact(policy)` 抛异常会传播。手动场景要自己决定怎么处理压缩失败。
:::

## 5. Compact Policy：压缩 memory snapshot

`CompactPolicy` 处理的是 `MemorySnapshot`，不是单条消息。

```java
public interface CompactPolicy {
    CompactResult compact(MemorySnapshot snapshot);
}
```

`StructuredSummaryCompactPolicy` 会：

1. 使用 `ContextProjector` 选出保留 items。
2. 生成带 `AI4J_COMPACT_SUMMARY` 标记的 summary。
3. 返回新的 `MemorySnapshot`。
4. 返回 `ContextReport`。

示例：

```java
AgentSession session = agent.newSession();

session.compact(new StructuredSummaryCompactPolicy(
    ContextBudget.builder()
        .maxItems(30)
        .pinnedPrefixItems(1)
        .build()
));

CompactResult result = session.getLastCompactResult();
System.out.println(result.getContextReport().getDroppedItemCount());
```

## 6. CompactResult 为什么是结构化的

`CompactResult` 不只是一个字符串 summary。它预留了结构化字段：

- `completed`
- `pending`
- `decisions`
- `changedArtifacts`
- `failedCommands`
- `testResults`
- `userConfirmations`
- `sandboxState`
- `openQuestions`
- `contextReport`

这些字段的填充情况按策略分：

| 策略 | 填哪些字段 |
| --- | --- |
| `StructuredSummaryCompactPolicy`（确定性） | 只填 `contextReport`；不做语义抽取 |
| `LlmCompactPolicy`（Tier-3） | 填 `completed` / `pending` / `decisions` / `failedCommands` / `testResults` / `openQuestions`，外加机械扫描得到的 `readFiles` / `modifiedFiles` |

`changedArtifacts` / `userConfirmations` / `sandboxState` 仍然留空——它们需要 workspace 和 approval 上下文，属于 `ai4j-coding` 的地盘，core agent 层不硬凑。

## 7. 与 AgentSession 的关系

P0-A 已经让 `AgentSession` 可以 snapshot / save / resume。P0-B 在此基础上增加：

- `AgentSession.compact(CompactPolicy)`
- `AgentSession.getLastCompactResult()`
- `AgentSessionSnapshot.compactResult`

这意味着：

```text
session compact 后的结构化结果可以随 snapshot 保存
resume 后仍能读取 last compact result
```

这对长任务恢复、UI 展示和远端 Runner 都很重要。

## 8. 与旧 MemoryCompressor 的兼容

已有 `AgentMemory` 实现仍然可以使用 `MemoryCompressor`。

如果想把新策略接到旧压缩入口，可以用：

```java
CompactPolicyMemoryCompressor compressor =
    new CompactPolicyMemoryCompressor(
        new StructuredSummaryCompactPolicy(ContextBudget.maxItems(20))
    );

InMemoryAgentMemory memory = new InMemoryAgentMemory(compressor);
```

这样写入 memory 时仍走旧的 compressor 机制，同时可以通过 `compressor.getLastResult()` 获取结构化结果。

## 9. 它和 Coding Agent Compact 的边界

`ai4j-agent` 的 compact 是通用 Agent SDK 能力，关注：

- memory snapshot
- model context projection
- compact result
- session snapshot

`ai4j-coding` 的 compact / checkpoint 以后会更宽，可能包含：

- workspace 文件变化
- shell 命令历史
- git diff
- browser 状态
- project run/test 状态
- approval 状态
- sandbox artifact

所以不要把 `ai4j-coding` 的全部 checkpoint 逻辑机械上移到 `ai4j-agent`。P0-B 只提供通用基座。

## 10. 适合自定义的点

你可以自定义：

| 扩展点 | 适合场景 |
| --- | --- |
| `ContextProjector` | 想按角色、工具结果、token 估算、RAG 相关性选择上下文 |
| `ContextBudget` | 想按模型、租户、任务类型设置预算 |
| `CompactPolicy` | 想用模型生成结构化 summary 或结合 event log/artifact 压缩 |
| `MemoryCompressor` | 想兼容旧 memory 写路径 |

:::note 自定义实现必须可诊断
最低要求：自定义实现必须让“保留了什么、丢了什么”可诊断。否则长程 Agent 出问题时很难定位。
:::

## 11. 当前限制

P0-B 是基础层，不包含：

- token 级精确预算（`ContextBudget` 的字符数是近似值）。
- event log 到 compact result 的完整提炼。
- sandbox artifact 的真实压缩。
- 远端 Runner 的 checkpoint 协议。

这些会在后续插件生命周期、Sandbox SPI、Coding Agent routing 和 Runner 任务里继续补齐。

## 12. 推荐继续阅读

- [Agent Session Runtime](/docs/agent/session-runtime)
- [Memory and State](/docs/agent/memory-and-state)
- [AI4J Agent SDK Roadmap](/docs/agent/sdk-roadmap)
- [Coding Agent Compact and Checkpoint](/docs/coding-agent/compact-and-checkpoint)

## Auto-compaction (runtime-triggered)

The runtime auto-compacts at the top of each step when the configured `CompactPolicy.shouldCompact`
returns true. Configure via `AgentBuilder.compactPolicy(...)`:

```java
// LLM-powered: uses the model to generate a structured summary of old items
LlmCompactPolicy policy = new LlmCompactPolicy(modelClient, "glm-5.1", 10);
Agent agent = Agents.react()
        .modelClient(modelClient).model("glm-5.1")
        .compactPolicy(policy)   // auto-compact when items > 10
        .build();
```

`LlmCompactPolicy` keeps the most recent N items and asks the LLM to summarize everything older.
For a mechanical (non-LLM) option, use `StructuredSummaryCompactPolicy` with a `ContextBudget`.

### Structured backfill

The summarizer is asked for a JSON object, which is parsed back into `CompactResult`:

```text
goal · completed · pending · decisions · errors · testResults · openQuestions
        ↓           ↓         ↓           ↓        ↓             ↓
   getCompleted  getPending  getDecisions  getFailedCommands  getTestResults  getOpenQuestions
```

The JSON is then rendered into a section-formatted summary (`## Goal`, `## Pending`, …) rather than
stored raw, because that summary is what gets injected as a system message on the next turn.

Parsing is deliberately forgiving — a summarizer is a model, not a schema-guaranteed API:

- ```` ```json ```` fences and surrounding prose are tolerated (first `{` to last `}` is extracted).
- A single string where an array was requested is accepted as a one-element list.
- **Unparseable output is not an error.** The raw text becomes the summary and the lists stay empty —
  exactly the pre-structured behaviour. A prose-returning model degrades, it does not break compaction.

Cross-compaction accumulation needs no extra state: the rendered summary already contains the
`Pending` and `Key Decisions` sections, and it is fed back in as `Previous summary` on the next
compaction, where the prompt instructs the model to carry unresolved entries forward. Because the
channel is the summary text itself, accumulation survives session save/resume — and cannot leak
between sessions the way a stateful policy instance would.

The `BEFORE_COMPACT` lifecycle hook fires before compaction (interception/customize);
`ON_COMPACT` fires after (observe).

### Turn-boundary safe cut

When deciding what to summarize vs keep, `LlmCompactPolicy` respects turn boundaries: the cut
point walks backwards from the naive position to the nearest user-role message. This prevents
leaving assistant messages or tool results orphaned without their preceding context.

### Cumulative file tracking

`LlmCompactPolicy` scans the items being summarized for tool calls referencing files
(`read`/`grep`/`find` → readFiles; `write`/`edit`/`create`/`delete` → modifiedFiles), deduplicates,
includes them in the summary prompt so the LLM knows what was touched, and carries them forward
in `CompactResult.readFiles` / `CompactResult.modifiedFiles` for the next compaction to accumulate.

### Restore keeps the summary

`AgentMemory.restore(snapshot)` 会把 `snapshot.getSummary()` 一起写回，不再只恢复 items。Tier-3
压缩产出的 summary 因此能跨 restore 存活——否则 resume/compact 一轮之后摘要就丢了，下一轮 prompt
取不到它。该行为同时覆盖默认实现和 `InMemoryAgentMemory` / `JdbcAgentMemory`。
