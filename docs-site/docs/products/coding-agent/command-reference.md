---
sidebar_position: 11
title: 命令参考
description: 汇总 Coding Agent 已实现的高频 slash 命令（provider/model/mcp/session/process/team/compact 等）的作用域、常用参数与 CLI/TUI/ACP 三种宿主的命令可见性差异。
tags: [reference]
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

### 自定义命令模板文件

`/commands`、`/palette`、`/cmd` 背后是同一套自定义命令模板机制（`CustomCommandRegistry`）。把常用提示词写成命令文件后，即可用 `/cmd <name>` 把正文注入当前 turn。

**发现路径**（后加载者覆盖同名命令）：

| 位置 | 作用域 |
| --- | --- |
| `~/.ai4j/commands/` | 用户全局，所有工作区可见 |
| `<workspace>/.ai4j/commands/` | 当前工作区，覆盖同名全局命令 |

支持的扩展名：`.md`、`.txt`、`.prompt`。命令名 = 文件名去掉扩展名（`review.md` → `/cmd review`）。

**文件格式**：第一行以 `#` 开头时，该行去掉 `#` 作为命令描述，第二行起才是 prompt 正文；第一行不以 `#` 开头时，整文件都是正文、无描述。正文里的 `$key` 占位符在渲染时按变量替换。

一个最小示例，`<workspace>/.ai4j/commands/refactor.md`：

```text
# 审阅并重构代码
请审阅当前 workspace 的代码，按 $language 的惯用法重构，并指出潜在问题。
```

随后在会话内执行 `/cmd refactor` 即把正文注入当前 turn。

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

## 7. 顶层 CLI 命令

除了 session 内的 slash command，`ai4j-cli` 本身还有一组顶层子命令。它们的入口在 `Ai4jCli`：

| 命令 | 作用 |
| --- | --- |
| `ai4j-cli code` | 启动 coding session（one-shot 或交互式 REPL），最常用的入口 |
| `ai4j-cli tui` | 等价于 `code --ui tui`，启动更丰富的文本 UI shell |
| `ai4j-cli acp` | 把 coding session 作为 ACP stdio server 启动（IDE / headless 集成） |
| `ai4j-cli run` | 跑一次单 agent 的 Agent Blueprint YAML（一次性、无 session） |
| `ai4j-cli trust` | 管理工作区钩子的信任目录（见 [生命周期钩子与工作区信任](/docs/products/coding-agent/lifecycle-hooks)） |
| `ai4j-cli extension` | 检查 / 装配 / 运行 classpath 上的 AI4J 扩展包 |

不带子命令、直接传 `--model` 等 flags 时，会按 `code` 命令处理，例如：

```bash
ai4j-cli --provider openai --model gpt-5-mini --prompt "Investigate why tests fail"
```

等价于 `ai4j-cli code ...`。

---

### 7.1 `ai4j-cli run` —— Agent Blueprint 一次性入口

`run`（对应 `AgentBlueprintRunCommand`）用于把一个声明式 Agent Blueprint YAML 跑一次。它不进入交互式 session，适合脚本化、CI 或单次任务：

```bash
ai4j-cli run agent.yaml --input "结合知识库回答" --provider openai --protocol responses
```

用法：

```text
ai4j-cli run <agent.yaml> --input <text> [options]
ai4j-cli run <agent.yaml> --prompt <text> [options]
```

`--input` 和 `--prompt` 是别名，二选一，必填（或通过 env 提供）。

选项：

| 选项 | 说明 |
| --- | --- |
| `--input` / `--prompt <text>` | 本次运行的用户输入（必填） |
| `--provider <name>` | 覆盖 YAML 里的 `model.provider` |
| `--protocol <chat\|responses>` | 覆盖协议 |
| `--model <name>` | 覆盖 YAML 里的 `model.model` |
| `--profile <name>` | 引用宿主侧 provider profile 元数据 |
| `--api-key <key>` | 宿主运行时 key；优先用 env / config |
| `--base-url <url>` | 兼容 provider 的运行时 base URL |
| `--workspace <path>` | provider/profile 配置查找的工作区 |
| `--allow-sandbox-declaration` | 接受 YAML 里 `sandbox.enabled` 声明，但不真正创建 sandbox |
| `--verbose` | 出现意外失败时打印完整 stack trace |
| `-h` / `--help` | 打印帮助 |

环境变量：`AI4J_AGENT_INPUT`、`AI4J_PROVIDER`、`AI4J_PROTOCOL`、`AI4J_MODEL`、`AI4J_API_KEY`、`AI4J_BASE_URL`，以及各 provider 专属 key（如 `OPENAI_API_KEY` / `ZHIPU_API_KEY` / `MINIMAX_API_KEY`）。

:::note YAML 不存密钥
`AgentFactory` 由宿主提供：YAML 文件不保存密钥、不安装插件、不创建真实 sandbox session。运行时凭证来自宿主 env / config。
:::

---

### 7.2 `ai4j-cli extension` —— 扩展包检查与运行

`extension`（对应 `CliExtensionCommand`）用于检查、装配和运行 classpath 上发现的 AI4J 扩展包（对应扩展 API 版本 `2.4.2`，groupId `io.github.lnyo-cly`）。

```text
ai4j-cli extension list
ai4j-cli extension inspect <id> [--runtime]
ai4j-cli extension plan <id> [--enable] [activation options]
ai4j-cli extension check <id> --enable [activation options]
ai4j-cli extension init <directory> --id <extension-id> --package <java-package> [options]
ai4j-cli extension validate <id>|--all
ai4j-cli extension run --enable <extension-id> [--allow-command <command>] <command> [arguments...]
ai4j-cli extension resource --enable <extension-id> [--allow-skill <name>|--allow-prompt <name>] <skill|prompt> <name>
```

子命令：

| 子命令 | 作用 |
| --- | --- |
| `list` | 列出已发现的扩展 manifest |
| `inspect <id>` | 展示 manifest、permissions、config prefix、source class；`--runtime` 额外列出贡献的 tools / commands / skills / prompts / guardrails / lifecycle hooks |
| `plan <id>` | 预览 enable / expose / allow 的激活状态，先不真正接入宿主 |
| `check <id> --enable` | 校验并在请求的激活资源未就绪时失败（pass/fail 门） |
| `init <directory>` | 在本地生成一个 Maven Java 8 插件骨架 |
| `validate <id>\|--all` | 校验 manifest、运行时资源与编写契约 |
| `run --enable <id> <command>` | 执行来自显式启用扩展的命令 |
| `resource --enable <id> <skill\|prompt> <name>` | 打印来自启用扩展的资源内容 |

激活选项（用于 `plan` / `check` / `run` / `resource`）：`--expose-tool`、`--allow-command`、`--allow-skill`、`--allow-prompt`、`--allow-guardrail`、`--strict`。

:::warning 运行扩展命令必须显式 --enable
classpath 发现（discovery）不会自动启用扩展。执行扩展命令或读取资源都必须先 `--enable <id>`，避免隐式执行任意扩展代码。
:::

`init` 选项：`--id`（必填，如 `weather-pack`）、`--package`（必填，如 `com.example.ai4j.weather`）、`--name`、`--group-id`（默认取 `--package`）、`--artifact-id`（默认取 `--id`）、`--version`（默认 `1.0.0`）、`--class-name`、`--vendor`。

---

## 8. `code` / `tui` CLI flags 完整参考

下面是 `code` / `tui` 命令（`CodeCommandOptionsParser`）支持的完整 flag 清单。所有 flag 都支持 `--name value` 和 `--name=value` 两种写法；布尔 flag 可以只写 `--name`（等价于 true）。

### 模型与协议

| Flag | 默认 | 说明 |
| --- | --- | --- |
| `--model <name>` | — | 模型名，**必填**（除非由 profile / env 提供） |
| `--provider <name>` | openai | provider，如 openai / zhipu / minimax / doubao / dashscope |
| `--protocol <chat\|responses>` | 由 provider/baseUrl 推导 | 不接受 `auto` |
| `--api-key <key>` | — | API key；优先用 env / config |
| `--base-url <url>` | — | 兼容 provider 的 base URL |

### 工作区与提示

| Flag | 默认 | 说明 |
| --- | --- | --- |
| `--workspace <path>` | 当前目录 | 工作区根目录 |
| `--workspace-description <text>` | — | 工作区描述文本 |
| `--system <text>` | — | 追加 system prompt |
| `--instructions <text>` | — | 追加 instructions |
| `--prompt <text>` | — | one-shot prompt；给了就走单次模式 |
| `--allow-outside-workspace` | false | 是否允许工具写出工作区 |
| `--ui <cli\|tui>` | cli | `tui` 命令会强制 `tui` |

### 采样与生成

| Flag | 默认 | 说明 |
| --- | --- | --- |
| `--max-steps <n>` | 0（不限） | agent 循环最大步数 |
| `--temperature <0..2>` | — | 采样温度 |
| `--top-p <0..1>` | — | nucleus sampling |
| `--max-output-tokens <n>` | — | 单次最大输出 token |
| `--parallel-tool-calls` | false | 是否允许并行工具调用 |
| `--stream` | true | 是否流式请求模型 |

### 会话

| Flag | 默认 | 说明 |
| --- | --- | --- |
| `--no-session` | false（未指定时取 env/property） | true = 仅内存会话，不持久化 |
| `--auto-save-session` | true | 是否自动保存 session |
| `--session-id <id>` | — | 指定 session id |
| `--resume <id>` / `--load <id>` | — | 恢复一个已保存 session（`--load` 是别名） |
| `--fork <id>` | — | 从已有 session fork 一个新分支 |
| `--session-dir <path>` | `<workspace>/.ai4j/sessions` | session 存储目录 |

:::warning session flag 互斥
`--resume` 和 `--fork` 不能同时使用；`--no-session` 不能与 `--resume` 或 `--fork` 组合。
:::

### 审批与 compact

| Flag | 默认 | 说明 |
| --- | --- | --- |
| `--approval <safe\|manual\|auto>` | safe | 审批模式，详见 [Tools 与审批机制](/docs/products/coding-agent/tools-and-approvals) |
| `--auto-compact` | true | 是否自动 compact |
| `--compact-context-window-tokens <n>` | 128000 | compact 用的上下文窗口 |
| `--compact-reserve-tokens <n>` | 16384 | compact 预留 token |
| `--compact-keep-recent-tokens <n>` | 20000 | compact 保留的最近 token |
| `--compact-summary-max-output-tokens <n>` | 400 | compact 摘要最大输出 token |

### 显示

| Flag | 默认 | 说明 |
| --- | --- | --- |
| `--theme <name>` | — | TUI 主题 |
| `--verbose` | false | 详细输出 |
| `-h` / `--help` | — | 打印帮助 |

多数 flag 都可以用环境变量（`AI4J_*`）或 Java property（`ai4j.*`）等价设置，例如 `AI4J_MODEL`、`AI4J_WORKSPACE`、`AI4J_STREAM`、`AI4J_APPROVAL`、`AI4J_SESSION_DIR` 等。

---

## 9. `/sandbox` 与 `/extension` slash 命令

这两条 slash 命令在 CLI/TUI 里用于运行时切换执行环境和检查扩展，都已实现（不再是规划中能力）。

### `/sandbox`

管理当前 session 的 sandbox 绑定（解析器是 `CliSandboxCommand`）。命令动作：

```text
/sandbox                                显示当前 sandbox 绑定状态
/sandbox status                         同上
/sandbox enable <provider> [options]    创建/绑定一个 sandbox，把 bash exec 路由过去
/sandbox attach <provider> <id> [options]  绑定一个已存在的 sandbox
/sandbox disable                        解除当前 sandbox 绑定，回到本地执行
```

`enable` / `attach` 的选项：

| 选项 | 说明 |
| --- | --- |
| `<provider>` | sandbox provider，当前支持 `daytona` |
| `<id>`（attach） | 要绑定的 sandbox id 或名称 |
| `--workspace` / `--sandbox-name <name>` | sandbox 名称 / 工作区 |
| `--sandbox-id <id>` | 显式指定 sandbox id |
| `--image` / `--snapshot <snapshot>` | sandbox 镜像 / 快照 |
| `--delete-on-close` | CLI 关闭或 disable 时删除 sandbox |
| `--keep-on-close` | 保留 sandbox（默认） |
| `--create-if-missing` / `--no-create-if-missing` | attach 目标缺失时是否创建 |

:::note 凭证不从命令行传
sandbox 的凭证必须来自环境变量或本地配置，**不接受** slash 命令参数传入——避免在 shell 历史里泄露密钥。
:::

绑定 sandbox 后，`bash action=exec` 会路由到 `SandboxSession.execute(...)`，并在返回结果里带上 `executionEnvironment`、`sandboxSessionId`、`sandboxProviderId`。完整边界见 [Sandbox Routing](/docs/products/coding-agent/sandbox-routing)。

### `/extension` / `/extensions`

在 session 内检查 / 运行 classpath 上的扩展包，对应顶层 `ai4j-cli extension` 的会话内入口：

```text
/extensions                              列出已发现的扩展插件
/extension list                          列出扩展
/extension inspect <id>                  查看 manifest 与运行时资源
/extension plan <id> [activation options]   预览激活状态
/extension check <id> --enable [options]    pass/fail 激活门
/extension validate <id>|--all           校验扩展契约
/extension run --enable <id> <command> [args]   运行扩展命令
/extension resource --enable <id> <skill|prompt> <name>  读取扩展资源
```

`/extension` 带补全：二级动作（list / inspect / plan / check / validate / run / resource）、资源类型（skill / prompt）以及激活选项（`--enable` / `--extension` / `--expose-tool` 等）都在补全候选里。

---

## 10. 建议阅读

如果你不是查表，而是想理解命令背后的用法，建议看：

1. [CLI / TUI 使用指南](/docs/products/coding-agent/cli-and-tui)
2. [配置体系](/docs/products/coding-agent/configuration)
3. [MCP 与 ACP](/docs/products/coding-agent/mcp-and-acp)
4. [会话、流式与进程](/docs/products/coding-agent/session-runtime)
5. [生命周期钩子与工作区信任](/docs/products/coding-agent/lifecycle-hooks)
