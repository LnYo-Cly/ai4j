---
sidebar_position: 11
---

# 命令参考

本页汇总 Coding Agent 当前已经实现的高频命令，并补充它们各自的作用域、常用参数和使用建议。

---

## 0. 先区分三种命令可见性

虽然命令都以 slash command 的形式出现，但三种宿主的命令面并不完全相同：

- `CLI`：最完整，适合直接终端使用
- `TUI`：最完整，并额外配合 palette、补全和面板
- `ACP`：只暴露适合 headless 宿主和 IDE 集成的一组安全子集

当前 ACP 默认暴露的标准命令集是：

- `/help`
- `/status`
- `/session`
- `/save`
- `/providers`
- `/provider`
- `/model`
- `/experimental`
- `/skills`
- `/agents`
- `/mcp`
- `/sessions`
- `/history`
- `/tree`
- `/events`
- `/team`
- `/compacts`
- `/checkpoint`
- `/processes`
- `/process`

也就是说：

- `provider/profile/model` 这类高价值配置命令，已经在 ACP 命令面板中暴露
- `theme`、`stream`、纯终端交互这类命令，仍更适合 CLI/TUI
- ACP 客户端应通过 `available_commands_update` 获取命令清单，而不是自行硬编码

---

## 1. Provider / Model / Runtime Flags

### `/providers`

列出已保存的 provider profiles。

```text
/providers
```

适合：

- 看当前机器上已经存了哪些 profile
- 排查 profile 名称拼错

---

### `/provider`

显示当前 effective provider 状态。

```text
/provider
```

通常会包含：

- 当前 active profile
- 当前 default profile
- effective provider
- effective protocol
- effective model

---

### `/provider use`

切换当前 workspace 正在使用的 profile，并立即重建当前 session runtime。

```text
/provider use <profile-name>
```

示例：

```text
/provider use zhipu-main
```

---

### `/provider save`

把当前运行中的 provider / protocol / model / baseUrl / apiKey 保存成 profile。

```text
/provider save <profile-name>
```

示例：

```text
/provider save openai-main
```

---

### `/provider add`

用显式参数新建 profile。

```text
/provider add <profile-name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]
```

示例：

```text
/provider add zhipu-main --provider zhipu --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4
```

说明：

- `--provider` 必填
- `--protocol` 省略时，会按 provider/baseUrl 推导默认协议
- 保存结果会写入 `~/.ai4j/providers.json`

---

### `/provider edit`

更新已有 profile。

```text
/provider edit <profile-name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]
```

示例：

```text
/provider edit zhipu-main --model glm-4.7-plus
/provider edit openai-main --protocol responses
/provider edit zhipu-main --clear-api-key
```

说明：

- 只会更新你显式传入的字段
- `--clear-model` / `--clear-base-url` / `--clear-api-key` 用于清空字段
- 如果修改的是当前 effective profile，会立即重建当前 session runtime

---

### `/provider default`

设置或清除全局默认 profile。

```text
/provider default <profile-name|clear>
```

示例：

```text
/provider default openai-main
/provider default clear
```

---

### `/provider remove`

删除一个已保存 profile。

```text
/provider remove <profile-name>
```

---

### `/model`

显示当前 effective model 与 workspace override。

```text
/model
```

---

### `/model <name>`

保存 workspace model override，并立即切换当前 session runtime。

```text
/model <name>
```

示例：

```text
/model glm-4.7-plus
```

---

### `/model reset`

清空 workspace model override，回退到 profile model。

```text
/model reset
```

---

### `/experimental`

查看或切换当前 workspace 的实验性 runtime 特性开关。

```text
/experimental
/experimental <subagent|agent-teams> <on|off>
```

示例：

```text
/experimental
/experimental subagent off
/experimental agent-teams on
```

说明：

- 当前状态会持久化到 `<workspace>/.ai4j/workspace.json`
- `subagent` 控制是否注入实验性的后台工作 subagent tool：`subagent_background_worker`
- `agent-teams` 控制是否注入实验性的交付团队 subagent tool：`subagent_delivery_team`
- 对应字段缺失时，当前实现按 `on (default)` 处理
- 切换后会立即重建当前 session runtime，让当前会话的可见工具集合生效
- 这个命令改变的是“当前 session 可见的 agent tool surface”，不是固定内置本地 Tool 列表
- 是否能稳定触发这些 agent tool，还取决于当前 provider / model 的 tool-calling 质量

---

## 2. Skills / MCP / Stream

### `/skills`

列出当前会话已发现的 coding skills。

```text
/skills
```

通常会包含：

- 当前发现到的 skill 数量
- workspace 配置文件位置
- 当前生效的 skill roots
- 每个 skill 的 `name / source / path / description`

---

### `/skills <name>`

查看单个 skill 的详细信息。

```text
/skills <skill-name>
```

示例：

```text
/skills repo-review
```

说明：

- 会显示 skill 的来源、路径、描述
- 会显示当前 skill roots，便于确认它是从哪里被发现的
- 只展示元信息，不会打印 `SKILL.md` 正文
- skill 名称可通过 slash 补全获得

---

### `/mcp`

显示当前 MCP 服务及状态。

```text
/mcp
```

常见用途：

- 看有哪些已注册服务
- 看 workspace 是否启用
- 看当前 session 是运行、暂停还是需要重连

---

### `/mcp add`

新增一个全局 MCP 服务。

```text
/mcp add --transport <stdio|sse|http> <name> <target>
```

用法说明：

- `stdio`：`target` 是命令行
- `sse` / `http`：`target` 是 URL

补充说明：

- 这里的 `http` 是 CLI 兼容参数名
- 实际保存到 MCP 配置时，推荐使用 `type: "streamable_http"`

---

### `/mcp enable|disable`

切换 workspace 级 MCP 启用状态。

```text
/mcp enable <name>
/mcp disable <name>
```

说明：

- 作用于当前 workspace 配置
- 影响后续 session runtime 可见的 MCP 服务集合

---

### `/mcp pause|resume`

切换当前 session 内的 MCP 运行状态。

```text
/mcp pause <name>
/mcp resume <name>
```

说明：

- `enable/disable` 是 workspace 配置层
- `pause/resume` 是当前 session 运行层

---

### `/mcp retry`

重连一个已启用的 MCP 服务。

```text
/mcp retry <name>
```

---

### `/mcp remove`

删除一个已注册的全局 MCP 服务。

```text
/mcp remove <name>
```

---

### `/stream`

显示当前 CLI 会话的模型请求 streaming 状态。

```text
/stream
```

---

### `/stream on|off`

切换当前 CLI 会话的模型请求 streaming 行为。

```text
/stream on
/stream off
```

说明：

- 作用域是当前 CLI 会话
- 切换时会立即重建当前 session runtime
- `on`：后续请求使用 `stream=true`
- `off`：后续请求使用 `stream=false`
- 这不是 provider 协议切换命令

---

## 3. 会话

### `/status`

显示当前 session 运行状态。

```text
/status
```

---

### `/session`

显示当前 session 元信息。

```text
/session
```

---

### `/save`

持久化当前 session 状态。

```text
/save
```

---

### `/sessions`

列出当前 session store 中的已保存 sessions。

```text
/sessions
```

---

### `/resume` / `/load`

恢复一个已保存 session。

```text
/resume <id>
/load <id>
```

说明：

- `/load` 是 `/resume` 的别名

---

### `/fork`

从已有 session fork 一个新分支。

```text
/fork [new-id]
/fork <source-id> <new-id>
```

---

### `/history`

显示从 root 到目标 session 的 lineage。

```text
/history [id]
```

---

### `/tree`

显示当前 session tree。

```text
/tree [id]
```

---

### `/events`

显示最近 session ledger events。

```text
/events [n]
```

---

### `/replay`

按 turn 聚合回放最近会话内容。

```text
/replay [n]
```

---

### `/team`

查看当前 agent team board，或管理工作区里已经持久化的 team snapshot。

```text
/team
/team list
/team status [team-id]
/team messages [team-id] [limit]
/team resume [team-id]
```

说明：

- `/team`：读取当前 session event ledger，经 `TeamBoardRenderSupport` 聚合成“当前会话里的 team board”
- `/team list`：列出 `<workspace>/.ai4j/teams/state/*.json` 中已知的 teamId
- `/team status [team-id]`：读取最近一次持久化的 `AgentTeamState` 并渲染文本版 board；`team-id` 省略时，默认取最近一个持久化 team
- `/team messages [team-id] [limit]`：读取 `<workspace>/.ai4j/teams/mailbox/<teamId>.jsonl`，用于看最近的团队协作消息
- `/team resume [team-id]`：重新打开一个“持久化快照视角”的 board，不会重新启动 team runtime，也不会重放 live team 执行
- `CLI` / `ACP`：返回文本版结果
- `TUI`：`/team` 打开当前 board，`/team resume ...` 打开持久化 board snapshot
- 当前 experimental delivery team 默认把数据写到 `<workspace>/.ai4j/teams`
- 只聚合 Team task / Team message，不会把普通 delegate task 混进来

---

### `/compacts`

查看最近 compact 历史。

```text
/compacts [n]
```

当前输出除了时间和摘要外，还会带 compact 诊断字段，例如：

- `strategy`
- `compactedToolResultCount`
- `deltaItemCount`
- `checkpointReused`
- `fallbackSummary`

---

### `/compact`

对当前 session memory 进行压缩。

```text
/compact
/compact <summary>
```

补充说明：

- 手工 compact 会直接更新当前 checkpoint；
- compact 成功后会清理上一轮残留的 pending loop artifact，并重置 auto-compact breaker；
- `<summary>` 可作为本次 compact 的附加总结指令，而不是替换整个 checkpoint schema。

---

### `/checkpoint`

显示当前结构化 checkpoint 摘要。

```text
/checkpoint
```

当前展示的 checkpoint 重点字段包括：

- `goal`
- `constraints`
- `done / in-progress / blocked`
- `keyDecisions`
- `nextSteps`
- `criticalContext`
- `processSnapshots`

---

## 4. 进程

### `/processes`

列出当前活跃和已恢复的进程元信息。

```text
/processes
```

---

### `/process status`

查看单个进程元信息。

```text
/process status <process-id>
```

---

### `/process follow`

查看进程元信息并跟随缓冲日志。

```text
/process follow <process-id> [limit]
```

---

### `/process logs`

读取某个进程的缓冲日志。

```text
/process logs <process-id> [limit]
```

---

### `/process write`

向活跃进程的 stdin 写入文本。

```text
/process write <process-id> <text>
```

---

### `/process stop`

停止一个活跃进程。

```text
/process stop <process-id>
```

---

## 5. TUI / Palette

### `/help`

输出当前命令帮助。

```text
/help
```

---

### `/theme`

查看或切换当前 TUI 主题。

```text
/theme
/theme <name>
```

---

### `/commands`

列出当前可用的自定义命令模板。

```text
/commands
```

---

### `/palette`

`/commands` 的别名，更偏向 TUI 交互语义。

```text
/palette
```

---

### `/cmd`

执行一个自定义命令模板。

```text
/cmd <name> [args]
```

---

### `/clear`

打印一个新的屏幕分区，相当于重新整理当前终端视图。

```text
/clear
```

---

### `/exit` / `/quit`

退出当前会话。

```text
/exit
/quit
```

---

## 6. 补全与交互约定

当前 TUI shell 下：

- `/`：打开命令面板
- `Tab`：应用当前补全项
- `Ctrl+P`：打开 command palette
- `Ctrl+R`：打开 replay
- `/team`：打开当前 team board
- `Enter`：提交输入
- `Esc`：活跃 turn 时中断当前任务；空闲时关闭面板或清空输入

当前状态栏文案含义：

- `Thinking`：分析当前输入和上下文
- `Connecting`：正在打开模型请求或等待首个模型事件
- `Responding`：模型正在持续输出
- `Working`：工具或进程仍在运行
- `Retrying`：请求正在重试
- `Waiting`：短时间内没有新进展
- `Stalled`：较长时间没有新进展，状态栏会提示 `press Esc to interrupt`

当前命令补全已覆盖：

- 根命令
- `/provider` 二级动作
- `/provider add|edit` 参数
- `/provider add|edit --protocol` 值
- `/model` 候选
- `/experimental` 的 feature / on|off 候选
- `/skills` 候选
- `/stream on|off`

---

## 7. 建议阅读

如果你不是查表，而是想理解命令背后的用法，建议看：

1. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
2. [配置体系](/docs/coding-agent/configuration)
3. [MCP 对接](/docs/coding-agent/mcp-integration)
4. [会话、流式与进程](/docs/coding-agent/session-runtime)
