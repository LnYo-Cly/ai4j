---
sidebar_position: 6
---

# Compact 与 Checkpoint 机制

`Coding Agent` 的长会话能力，不是简单把历史消息一直堆在 memory 里。

真正要解决的是三个工程问题：

- 上下文窗口有限，但代码任务往往是多轮、跨文件、跨进程的；
- 工具输出可能非常大，尤其是 `bash`、测试日志、构建日志和文件 diff；
- compact 之后如果不能继续稳定工作，outer loop 很容易丢语义、重复劳动，甚至空转。

这一页专门说明 `ai4j-coding` 当前 compact / checkpoint 管线的职责划分、工作原理、公开 API 和调优入口。

---

## 1. 设计边界

这套机制刻意没有塞回 `ai4j-agent` 的 `BaseAgentRuntime`。

当前边界是：

- `BaseAgentRuntime`：保持底层单轮 tool-loop 语义，不改变传统 Agent 的兼容行为；
- `CodingSession`：维护 coding session 的 memory、checkpoint、process snapshot、compact 状态；
- `CodingAgentLoopController`：负责任务级 outer loop 和 compact 后 continuation；
- `CodingSessionCompactor`：负责 checkpoint compact；
- `CodingToolResultMicroCompactor`：负责较大 tool result 的轻量压缩。

这样做的原因是：

- 普通 `IChatService` / `AiService` / `Agent` 仍保留原有调用语义；
- Coding Agent 可以单独演进 outer loop、resume、fork、checkpoint、process 管理；
- CLI / TUI / ACP 可以共享同一套 session compact 机制，而不是各自重复实现。

---

## 2. 哪些对象是核心入口

| 类 | 作用 | 为什么重要 |
| --- | --- | --- |
| `CodingSession` | 会话主对象，提供 `run`、`compact`、`snapshot`、`exportState`、`restore` | 这是公开 API 的第一入口 |
| `CodingSessionCompactor` | checkpoint compact 主流程 | 负责切片、总结、fallback、strategy 计算 |
| `CodingToolResultMicroCompactor` | 老旧大 tool result 的微压缩 | 优先降上下文噪音，避免不必要的 checkpoint 总结 |
| `CodingSessionCheckpoint` | 结构化 checkpoint 数据模型 | compact 之后真正留给后续轮次使用的“任务摘要” |
| `CodingSessionCompactResult` | 一次 compact 的诊断结果 | CLI `/compacts`、ACP/headless 事件、状态页都会用到 |
| `CodingSessionSnapshot` | 轻量会话快照 | 面向状态展示和 UI |
| `CodingSessionState` | 完整持久化状态 | 面向 save / resume / fork |
| `CodingContinuationPrompt` | compact 后 continuation 隐藏 prompt | 用于重新锚定 checkpoint，减少语义漂移 |
| `CodingAgentLoopController` | outer loop 控制器 | 决定何时继续、何时停止、何时把 compact 结果重新注入 |

源码入口主要在：

- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingSession.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/compact/CodingSessionCompactor.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/compact/CodingToolResultMicroCompactor.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingAgentLoopController.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingContinuationPrompt.java`

---

## 3. 对外怎么用

### 3.1 Java API

```java
CodingAgent agent = CodingAgents.builder()
        .modelClient(modelClient)
        .model("gpt-5-mini")
        .workspaceContext(workspaceContext)
        .codingOptions(CodingAgentOptions.builder()
                .autoCompactEnabled(true)
                .build())
        .build();

try (CodingSession session = agent.newSession()) {
    session.run("Read the repository and prepare a refactor plan.");

    CodingSessionCompactResult compactResult = session.compact();
    CodingSessionSnapshot snapshot = session.snapshot();
    CodingSessionState state = session.exportState();

    // 稍后可恢复
    try (CodingSession restored = agent.newSession(state)) {
        restored.run("Continue the same task.");
    }
}
```

`CodingSession` 侧与 compact 直接相关的公开能力包括：

- `compact()` / `compact(String summary)`：手工触发 compact；
- `snapshot()`：拿轻量会话快照，适合 `/status`、状态面板、调试输出；
- `exportState()`：导出完整持久化状态；
- `restore(CodingSessionState)`：恢复完整状态；
- `drainPendingAutoCompactResults()`：取走本轮自动 compact 结果，用于宿主事件流；
- `getLastAutoCompactResult()` / `getLatestCompactResult()`：读取最近 compact 诊断。

### 3.2 CLI / ACP

命令入口主要是：

- `/compact`
- `/compacts`
- `/checkpoint`
- `/session`
- `/status`

其中：

- `/compact` 是主动压缩；
- `/compacts` 看 compact 历史和诊断字段；
- `/checkpoint` 看结构化 checkpoint 本身；
- `/session` / `/status` 更多是看当前会话状态与最近 compact 摘要。

---

## 4. Checkpoint 里到底存什么

`CodingSessionCheckpoint` 不是“随便拼一段摘要文本”，而是固定结构：

- `goal`
- `constraints`
- `doneItems`
- `inProgressItems`
- `blockedItems`
- `keyDecisions`
- `nextSteps`
- `criticalContext`
- `processSnapshots`
- `generatedAtEpochMs`
- `sourceItemCount`
- `splitTurn`

这意味着 compact 后留下的不是松散文字，而是更接近任务状态机的摘要。

`CodingSessionCheckpointFormatter` 当前支持两种来源：

- 结构化 JSON，总结模型优先按该 schema 返回；
- 已渲染的 markdown checkpoint，恢复时也能再解析回结构化对象。

所以 checkpoint 既适合：

- 让模型继续工作；
- 也适合在 CLI/TUI/ACP 中做人类可读展示。

---

## 5. Compact 管线怎么工作

### 5.1 触发点

compact 有两类入口：

- 手工：`session.compact()` 或 CLI `/compact`
- 自动：每轮 `runSingleTurn()` 结束后，`CodingSession.maybeAutoCompactAfterTurn()`

自动 compact 只在 `CodingAgentOptions.autoCompactEnabled=true` 时生效。

### 5.2 第一层：tool-result microcompact

这是最轻的一层，目标是优先处理“旧的、巨大的、但不值得完整保留”的 `function_call_output`。

`CodingToolResultMicroCompactor` 会：

- 先估算当前上下文 token；
- 找出所有 `function_call_output`；
- 保留最近若干条 tool result 不动；
- 只压缩超出阈值的旧 tool result；
- 把原始大输出替换成带 preview 的短摘要。

这层的特点是：

- 不依赖总结模型；
- 不改 checkpoint；
- 适合快速回收日志型上下文空间；
- 成功时 `strategy = tool-result-micro`。

### 5.3 第二层：checkpoint compact 准备

如果 microcompact 之后上下文仍然超预算，就进入 `CodingSessionCompactor.prepare(...)`。

这里会把 memory 切成三段：

- `itemsToSummarize`：需要总结进 checkpoint 的较旧历史；
- `turnPrefixItems`：如果切点落在一次未完成 turn 中，会把较早前缀单独拿出来；
- `keptItems`：最近保留在 memory 里的消息，不直接压进摘要。

这一步的核心目的是：

- 尽量保留最近上下文原文；
- 避免把“正在进行的当前 turn”硬切断；
- 必要时标记 `splitTurn=true`，供后续 continuation 做再锚定。

### 5.4 第三层：生成或更新 checkpoint

`CodingSessionCompactor.buildCheckpoint(...)` 会区分两种情形：

- 没有旧 checkpoint：走初始总结；
- 已有旧 checkpoint：走 checkpoint + delta 更新。

也就是：

- `checkpoint`
- `checkpoint-delta`

旧 checkpoint 不会直接丢弃，而是被视作“已有 session memory”继续累积。

### 5.5 第四层：aggressive compact

如果正常 checkpoint compact 完成后，`keptItems + summary` 仍然太大，还会进入 aggressive path。

这时会：

- 对更大的上下文范围重新做一次更激进的总结；
- 把 `keptItems` 清空或进一步压缩；
- 返回：
  - `aggressive-checkpoint`
  - `aggressive-checkpoint-delta`

这层不是默认路径，而是“正常 compact 之后仍压不下来”的二次保险。

### 5.6 第五层：恢复 memory 并发布结果

compact 完成后，`CodingSession` 会把 memory 恢复成：

- `keptItems`
- `rendered checkpoint summary`

随后更新：

- `checkpoint`
- `latestCompactResult`
- auto compact 相关状态

CLI、TUI、ACP 看到的 compact 信息，本质上都来自这一步生成的 `CodingSessionCompactResult`。

---

## 6. Compact 结果字段怎么读

`CodingSessionCompactResult` 当前包含这些关键字段：

| 字段 | 含义 |
| --- | --- |
| `beforeItemCount` / `afterItemCount` | compact 前后 memory item 数量 |
| `summary` | 供展示的人类可读摘要或 checkpoint 文本 |
| `automatic` | 是否来自自动 compact |
| `splitTurn` | 是否切到了未完成 turn |
| `estimatedTokensBefore` / `estimatedTokensAfter` | compact 前后估算 token |
| `strategy` | 本次 compact 走了哪条路径 |
| `compactedToolResultCount` | microcompact 压了多少条 tool result |
| `deltaItemCount` | 本次并入 checkpoint 的 delta item 数量 |
| `checkpointReused` | 是否复用了旧 checkpoint |
| `fallbackSummary` | 是否用了 fallback summary，而不是模型成功总结 |
| `checkpoint` | 结构化 checkpoint 本身 |

### 6.1 `strategy` 的语义

当前会出现这些值：

| strategy | 说明 |
| --- | --- |
| `tool-result-micro` | 只做了旧 tool result 轻量压缩 |
| `checkpoint` | 新建 checkpoint |
| `checkpoint-delta` | 复用旧 checkpoint 并合并新 delta |
| `aggressive-checkpoint` | 普通 checkpoint 仍过大，改走激进压缩 |
| `aggressive-checkpoint-delta` | 在已有 checkpoint 上做激进 delta compact |

### 6.2 `fallbackSummary` 的语义

`fallbackSummary=true` 不等于 compact 失败。

它表示：

- 最终 compact 仍然成功完成；
- 但摘要不是由总结模型正常生成，而是走了本地 fallback 逻辑。

这在长会话里非常关键，因为它允许 session 在总结模型不可用时继续工作，而不是整轮会话直接报错。

---

## 7. 失败与回退路径

### 7.1 prompt-too-long retry

如果 compact 的“总结请求本身”就超了模型上下文，当前不会立刻失败。

`CodingSessionCompactor.summarize(...)` 会：

- 裁掉最老一部分待总结片段；
- 重新生成总结请求；
- 重试到上限。

如果最终还是不行，再进入 fallback。

### 7.2 本地 fallback summary

如果没有可用总结模型，或者总结调用异常，当前会尝试本地 fallback checkpoint。

这个 fallback 会尽量保留：

- 最新用户目标；
- 最近几条关键信息；
- tool error / approval block 之类的重要信号。

### 7.3 session-memory fallback

如果已经存在旧 checkpoint，这时 fallback 不会简单丢掉旧摘要重来。

相反，它会：

- 复用已有 checkpoint；
- 把最近 delta context 合并进去；
- 标记 `fallbackSummary=true`；
- 让后续 compact / continuation 仍能以“checkpoint + recent delta”的方式继续。

这也是当前实现里“session-memory-first”的实际落点。

### 7.4 auto-compact circuit breaker

如果自动 compact 连续失败达到阈值，`CodingSession` 会打开 breaker：

- 停止继续自动 compact；
- 避免 outer loop 因 compact 反复失败而空转；
- 将错误保留到宿主状态里。

手工 `compact()` 成功后会重置这组失败计数和 breaker 状态。

---

## 8. Compact 和 outer loop 是怎么接起来的

compact 真正难的地方不是“生成摘要”，而是“摘要生成后还能不能继续稳定工作”。

当前 `CodingAgentLoopController` 会在决定 auto-continue 时，把 compact 结果交给 `CodingContinuationPrompt`。

隐藏 continuation prompt 会重新注入这些信息：

- compact strategy
- checkpoint goal
- constraints
- blocked items
- next steps
- critical context
- in-progress items
- process snapshots

如果 checkpoint 来自 split-turn compact，还会显式提醒模型：

- 当前摘要只覆盖了 turn 的前半段；
- 最近 kept messages 才是最新 turn tail。

这层再锚定的意义是：

- 降低 compact 边界上的语义漂移；
- 避免模型把 compact 后的 continuation 当成新任务；
- 避免“明明刚总结完，又问用户重复信息”。

---

## 9. 该调哪些配置

与 compact / outer loop 最相关的配置在 `CodingAgentOptions`：

| 配置 | 作用 |
| --- | --- |
| `autoCompactEnabled` | 是否启用自动 compact |
| `compactContextWindowTokens` | 允许使用的上下文窗口预算 |
| `compactReserveTokens` | 给模型输出保留的 token 空间 |
| `compactKeepRecentTokens` | compact 时尽量原样保留的最近上下文预算 |
| `compactSummaryMaxOutputTokens` | 总结模型生成 checkpoint 的最大输出 |
| `toolResultMicroCompactEnabled` | 是否启用 microcompact |
| `toolResultMicroCompactKeepRecent` | 最近多少条 tool result 不压 |
| `toolResultMicroCompactMaxTokens` | 单条 tool result 超过多少 token 才压 |
| `autoCompactMaxConsecutiveFailures` | 自动 compact 连续失败多少次后打开 breaker |
| `autoContinueEnabled` | 是否允许 outer loop 自动继续 |
| `maxAutoFollowUps` | 自动继续的最大 follow-up 次数 |
| `maxTotalTurns` | 单个用户任务最多允许多少 turn |
| `continueAfterCompact` | compact 之后是否允许继续当前任务 |
| `stopOnApprovalBlock` | 审批拒绝时是否停住 |
| `stopOnExplicitQuestion` | 模型显式向用户提问时是否停住 |

一个常见调优原则是：

- 先用默认值；
- 如果日志/测试输出太大，先调 microcompact；
- 如果模型经常在 compact 后丢任务，优先检查 `compactKeepRecentTokens` 与 continuation 再锚定；
- 如果 provider 容易在总结阶段报长度错误，再调 `compactSummaryMaxOutputTokens` 与上下文预算。

---

## 10. `snapshot()` 和 `exportState()` 的区别

这两个接口名字接近，但用途不同。

### 10.1 `CodingSessionSnapshot`

偏“状态展示对象”，用于：

- `/status`
- `/session`
- TUI 状态面板
- headless/ACP 的轻量状态查看

它保留的是：

- 当前 checkpoint goal
- 最近 compact 模式与摘要
- token 估算
- breaker 状态
- 进程概况

### 10.2 `CodingSessionState`

偏“完整恢复对象”，用于：

- save / resume
- fork
- 跨宿主持久化

它保留的是：

- 原始 memory snapshot
- process snapshots
- checkpoint
- latestCompactResult
- auto compact failure / breaker 状态

一句话区分：

- `snapshot()` 给人看
- `exportState()` 给 runtime 恢复

---

## 11. 宿主侧会看到什么

### 11.1 CLI / TUI

`CodingCliSessionRunner` 会把 compact 结果转成：

- `/compacts` 可读诊断行；
- `/checkpoint` 的结构化摘要；
- `/session` / `/status` 中的最近 compact 状态。

### 11.2 ACP / Headless

`HeadlessCodingSessionRuntime` 会把 compact 结果作为结构化事件 payload 发给宿主。

这意味着宿主不必重新理解 compact 逻辑，只需要消费这些字段：

- `strategy`
- `compactedToolResultCount`
- `deltaItemCount`
- `checkpointReused`
- `fallbackSummary`

---

## 12. 推荐连读

1. [会话、流式与进程](/docs/coding-agent/session-runtime)
2. [Coding Agent Architecture](/docs/coding-agent/architecture)
3. [配置体系](/docs/coding-agent/configuration)
4. [命令参考](/docs/coding-agent/command-reference)
