---
sidebar_position: 5
---

# Prompt 组装与上下文来源

`Coding Agent` 的行为，不是只由当前这句用户输入决定。

一次真正发给 runtime 的请求，通常同时包含：

- `systemPrompt`：全局角色和硬约束；
- workspace 指令：工作区、内置工具、shell 约束、skills 目录等；
- `instructions`：本次会话或本次启动追加的任务规则；
- session state：历史上下文、checkpoint、compact 摘要；
- 当前用户输入：普通提问、`--prompt`、ACP `session/prompt`、`/cmd` 渲染结果；
- tool schema：内置工具与 MCP 工具的定义。

理解这几层的来源，才能正确判断“为什么模型会这样回答”。

---

## 1. 最终请求是怎么拼出来的

可以先用下面这张心智模型理解：

```text
systemPrompt
+ workspace instructions
+ instructions
+ session memory / compact summary
+ current user turn
+ tool schemas
```

需要注意两点：

- 这不是简单的纯字符串拼接。
- `tool schemas`、session state、模型采样参数等，会作为独立结构一并交给 Agent runtime。

其中，`Coding Agent` 当前只会在构建阶段把 workspace 相关说明追加进 `systemPrompt`；`instructions`、当前用户输入、工具定义与会话状态仍是分层传入。

---

## 2. System Prompt 层

这一层通常来自两种入口：

- CLI / TUI：`--system`
- Java API：`CodingAgents.builder().systemPrompt(...)`

这一层适合放：

- 全局角色定义；
- 不希望每轮都重复写的行为约束；
- 团队级编码规范；
- 输出格式硬要求。

这层内容会跟随 session 一起保存、恢复和 fork，因此它更像“会话级基线”，不是一次性用户输入。

---

## 3. Workspace 指令层

这是 `Coding Agent` 区别于普通聊天 Agent 的关键。

默认情况下，`CodingAgentOptions.prependWorkspaceInstructions = true`，因此构建 agent 时会自动把工作区上下文合并进 `systemPrompt`。这一层通常包含：

- workspace root 路径；
- workspace description；
- 当前内置工具列表：`bash`、`read_file`、`write_file`、`apply_patch`；
- 工具调用规则；
- `apply_patch` 语法要求；
- 当前操作系统对应的 shell 使用说明；
- 是否允许读写工作区外文件；
- 当前发现到的 skills 列表。

这一层的作用不是“告诉模型要做什么业务”，而是“告诉模型它现在身处什么执行环境、能用什么工具、必须遵守什么约束”。

### 3.1 Workspace Description

CLI/TUI 启动时可以通过 `--workspace-description` 传入对仓库的简要说明。

适合放：

- 仓库用途；
- 主要模块划分；
- 关键技术栈；
- 当前任务背景。

不适合放：

- 每轮都变的临时需求；
- 很长的业务文档；
- 想强行覆盖系统规则的说明。

---

## 4. Instructions 层

这一层通常来自：

- CLI / TUI：`--instructions`
- Java API：`CodingAgents.builder().instructions(...)`

它适合表达：

- 当前会话的任务边界；
- 本轮工作方法；
- 长于单次输入、但又不应该固化到 `systemPrompt` 的额外说明。

一个实用区分方法：

- 长期稳定规则：放 `systemPrompt`
- 当前会话策略：放 `instructions`
- 当前这一轮要做的事：放用户输入

---

## 5. 当前用户输入层

这一层是每一轮真正变化的部分，来源包括：

- one-shot `--prompt`
- 交互式 CLI/TUI 输入框里的文本
- ACP `session/prompt` 中的 `prompt[]`
- `/cmd <name> [args]` 渲染后的命令模板文本

### 5.1 ACP 的 `session/prompt`

ACP 宿主传入的是结构化 `prompt[]` 数组，但当前实现会先把它展平成连续文本，再作为本轮输入提交给 session runtime。

因此在 ACP 侧应把它理解为：

- 宿主传输层是结构化的；
- Coding Agent 当前消费层是“按顺序拼接后的文本输入”。

### 5.2 Custom Commands 的作用

`/cmd` 不是额外的系统提示机制，而是“用户输入模板”。

当前会从两个目录读取命令模板：

- `~/.ai4j/commands`
- `<workspace>/.ai4j/commands`

支持的文件扩展名包括：

- `.md`
- `.txt`
- `.prompt`

模板渲染时可使用这些变量：

- `$ARGUMENTS`
- `$WORKSPACE`
- `$SESSION_ID`
- `$ROOT_SESSION_ID`
- `$PARENT_SESSION_ID`

渲染完成后，结果会直接作为一轮新的用户输入发送给 agent。也就是说，`/cmd` 的本质是 prompt macro，而不是替换系统层。

---

## 6. Skills、Tools、MCP 分别怎么进入上下文

这三类能力很容易被混在一起，但来源不同。

### 6.1 Skills

skills 会先被发现，再以“可用技能目录”的形式写入 workspace 指令层。

默认发现路径包括：

- `<workspace>/.ai4j/skills`
- `~/.ai4j/skills`
- 额外配置的 `skillDirectories`

注意：

- 模型看到的是 skill 清单，而不是自动加载全部 `SKILL.md` 正文；
- 真正需要使用某个 skill 时，模型仍应先通过 `read_file` 读取对应 `SKILL.md`。

### 6.2 Tools

内置工具不是普通文本提示，而是实际注册到 tool registry 的结构化工具定义。

当前内置工具主要是：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

workspace 指令层只负责告诉模型“这些工具存在，以及调用规则是什么”；真正可调用的 schema 与执行器，来自 `CodingAgentBuilder` 在构建阶段注入的内置 registry / executor。

### 6.3 MCP

MCP 工具也不是通过拼 prompt 注入的。

它们的进入方式是：

1. CLI/ACP 侧准备 MCP runtime；
2. 将 MCP tool registry / executor 挂到 `CodingAgentBuilder`；
3. 模型在当前会话里把它们当作普通工具调用。

所以 MCP 对行为的影响主要在“可调用工具集合”层，而不是“额外塞一段说明文字”。

---

## 7. Session Memory 与 Compact

如果启用了 session，那么每轮请求还会携带历史上下文。

这部分不是简单“把之前所有问答全文重贴一遍”，而是由 session state 管理，包括：

- 已有对话历史；
- tool call / tool result；
- checkpoint；
- compact 后的摘要结果。

当前默认：

- `autoCompact = true`
- `compactContextWindowTokens = 128000`
- `compactReserveTokens = 16384`
- `compactKeepRecentTokens = 20000`
- `compactSummaryMaxOutputTokens = 400`

当前 compact 管线不是单一步骤，而是分层执行：

- 先尝试 `tool-result microcompact`，只压缩较旧且超大的 `function_call_output`
- 若仍超预算，再走 checkpoint compact
- summary 请求本身遇到 `prompt-too-long` 一类错误时，会裁掉最老待总结片段后重试
- 连续 auto compact 失败达到阈值后，会打开 breaker，避免 outer loop 反复空转

你可以配合 `/compacts` 观察最近 compact 的诊断字段，例如：

- `strategy`
- `compactedToolResultCount`
- `deltaItemCount`
- `checkpointReused`

如果 compact 后 outer loop 继续执行，隐藏 continuation prompt 还会把 checkpoint 的 goal / constraints / blocked / next steps / critical context / process snapshots 再注入一次，减少 compact 边界上的语义漂移或重复劳动。

如果已有 checkpoint，而新的 compact summary 模型暂时不可用，runtime 还会优先走本地的 session-memory fallback：复用已有 checkpoint，并把最近 delta context 合并进去，而不是直接让 compact 整体失败。

因此当上下文很长时，旧内容会被压缩成摘要，而不是无限增长。

这也是为什么同样一句新输入，在“新 session”和“历史很长的老 session”里，行为可能不同。

如果你需要看 compact 各阶段的源码分工、strategy 字段、fallback 路径和公开 API，请直接读：

- [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)

---

## 8. 排查行为偏差时先看什么

如果你发现 agent 的回答或执行方式不符合预期，建议按这个顺序检查：

1. `systemPrompt` 是否写入了过强或互相冲突的规则
2. `workspaceDescription` 是否把项目背景说偏了
3. `instructions` 是否覆盖了你真正想要的策略
4. 当前输入是否其实来自 `/cmd` 渲染结果，而不是裸文本
5. 目标 skill 是否真的被发现，并且对应 `SKILL.md` 可读
6. MCP/runtime 中是否挂入了你预期之外的工具
7. 当前 session 是否已经发生 compact，导致旧上下文被摘要化

---

## 9. 推荐阅读

1. [会话、流式与进程](/docs/coding-agent/session-runtime)
2. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
3. [Skills 使用与组织](/docs/coding-agent/skills)
4. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
5. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
