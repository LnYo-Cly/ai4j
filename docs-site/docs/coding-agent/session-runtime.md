---
sidebar_position: 4
---

# 会话、流式与进程

Coding Agent 的使用体验，本质上取决于三件事：

- 会话如何保存和恢复；
- 流式文本如何呈现；
- 后台进程如何管理。

---

## 1. 会话模型

当前 Coding Agent 支持持续会话，意味着一次运行里不只是“问一次答一次”，而是带有上下文、事件和分支的会话树。

常用能力包括：

- `save`
- `resume`
- `fork`
- `history`
- `tree`
- `events`
- `replay`
- `compacts`

同一个用户 prompt 在内部也不一定只跑一轮模型调用。

当前 `CodingSession` 已经接入受控 outer loop：

- 一轮工具工作结束后，可以自动继续后续回合；
- auto compact 完成后，可以带着压缩后的上下文继续；
- compact 后的 continuation 会显式从 checkpoint goal / constraints / blocked / next steps / critical context / process snapshots 再锚定；
- 遇到显式提问、审批阻塞、工具错误或预算上限时停止；
- continuation 使用隐藏 prompt，不会被当成新的用户消息写进会话历史。

---

## 2. Java API 视角

如果你不是通过 CLI，而是直接在 Java 里接 `CodingAgent`，这一层最值得记住的是 `CodingSession`。

常用接口包括：

- `run(...)` / `runStream(...)`
- `compact()` / `compact(String summary)`
- `snapshot()`
- `exportState()`
- `restore(CodingSessionState)`

它们分别对应三类用途：

- 执行任务；
- 主动压缩当前 session memory；
- 获取轻量状态或完整持久化状态。

其中：

- `CodingSessionSnapshot` 偏展示对象，适合 `/status`、状态页、调试输出；
- `CodingSessionState` 偏恢复对象，适合 save / resume / fork；
- `CodingSessionCompactResult` 是 compact 的诊断载体，包含 `strategy`、`checkpointReused`、`fallbackSummary` 等字段。

compact 的内部机制和字段说明，建议直接连读：

- [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)

---

## 3. 恢复与分支

### 3.1 恢复

```text
/sessions
/resume <id>
/load <id>
```

适合：

- 回到之前保存的上下文；
- 重看某个仓库中的历史工作；
- 在同一个任务上接着做。

当前 save / resume 除了 memory 与 process snapshot，也会保留最近 compact 结果以及 auto-compact breaker 状态，因此恢复后 `/status`、`/session`、`/compacts` 看到的 compact 诊断不会直接丢失。

### 3.2 分支

```text
/fork [new-id]
/fork <source-id> <new-id>
```

适合：

- 在不破坏原会话的前提下尝试另一种方案；
- 对比不同模型或不同提示策略；
- 保留“主线”和“实验线”。

---

## 4. 历史与事件

当前提供两种视角：

- 面向用户的会话视角：`/history`、`/tree`、`/replay`
- 面向底层事件的账本视角：`/events`、`/compacts`

推荐用法：

- 想快速回顾问答过程：先用 `/replay`
- 想理解会话分支：用 `/tree`
- 想排查具体发生了什么：看 `/events`

当前事件账本里，除了常规 message/tool/compact 事件，还会出现这些 outer loop 事件：

- `AUTO_CONTINUE`
- `AUTO_STOP`
- `BLOCKED`

它们用于回答一个任务级问题：为什么这一轮继续了，或者为什么停了。

---

## 5. 流式输出

### 5.1 流式不是单 token 协议

无论是 CLI/TUI 还是 ACP，流式文本都应理解为“按顺序到达的文本增量”，而不是“每个事件必然等于一个 token”。

上游 provider 可能一次返回：

- 一个字符；
- 一个词；
- 一小段文本；
- 一段包含换行的内容。

### 5.2 使用建议

- 保留增量中的换行和空白；
- 不要先做 `trim()`；
- 如果需要平滑显示，在 UI 层缓冲，而不是改协议层。

---

## 6. 审批模式

当前支持：

- `auto`
- `safe`
- `manual`

启动参数：

```text
--approval <auto|safe|manual>
```

含义可以简单理解为：

- `auto`：默认自动放行；
- `safe`：更保守地处理有风险的工具调用；
- `manual`：每次敏感工具都要求确认。

在 CLI/TUI 下，审批通过终端交互完成；在 ACP 下，审批通过 `session/request_permission` 往返完成。

---

## 7. 后台进程

内置 `bash` 工具不仅可以执行一次性命令，也可以管理长期运行进程。

常用命令：

- `/processes`
- `/process status <id>`
- `/process follow <id> [limit]`
- `/process logs <id> [limit]`
- `/process write <id> <text>`
- `/process stop <id>`

典型用途：

- 启动开发服务；
- 跟踪构建或测试日志；
- 给交互式进程写 stdin；
- 停止失控任务。

---

## 8. 继续阅读

1. [命令参考](/docs/coding-agent/command-reference)
2. [ACP 集成](/docs/coding-agent/acp-integration)
3. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
