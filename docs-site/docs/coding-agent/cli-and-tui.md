---
sidebar_position: 3
---

# CLI / TUI 使用指南

`code` 和 `tui` 使用同一套 Coding Agent 会话、工具、模型和 MCP 运行时，差别主要在交互壳层。

如果你是直接在终端里使用 AI4J Coding Agent，这一页应该是主入口；如果你要接 IDE 或自定义宿主，则继续看 [ACP 集成](/docs/coding-agent/acp-integration)。

---

## 1. 三种最常见的启动方式

### 1.1 one-shot

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Read README and summarize the project structure"
```

适合：

- 先验证 provider / model 是否能通
- 做一次性任务
- 被脚本或 CI 触发

### 1.2 持续 CLI 会话

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar code `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

适合：

- 在普通终端里持续交互
- 想保留 session / process / MCP 状态
- 不需要全屏 TUI

### 1.3 TUI

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

适合：

- 长时间停留在一个仓库里
- 高频使用 slash command、palette、replay、team board、process 管理
- 希望把状态栏、补全和 transcript 放到同一界面里

---

## 2. `code` 和 `tui` 的关系

- `code`：普通 CLI / REPL，适合轻量使用
- `tui`：更完整的文本 UI，适合长期交互
- 两者都支持同一套会话、模型、命令、MCP、技能和进程能力

---

## 3. 启动时最重要的参数

- `--provider`：平台类型，例如 `openai`、`zhipu`
- `--protocol`：协议族，只支持 `chat` 和 `responses`
- `--model`：模型名
- `--base-url`：兼容 host 或供应商专用入口
- `--workspace`：工作区根目录，关系到 session、skills、workspace 配置与 MCP 启用状态
- `--approval`：`auto`、`safe`、`manual`
- `--prompt`：只在 one-shot 模式使用

---

## 4. 协议选择：现在只有 `chat` 和 `responses`

CLI 当前只对用户暴露两种协议：

- `chat`
- `responses`

如果不显式传 `--protocol`，默认规则是：

- `openai` + 官方 OpenAI host：`responses`
- `openai` + 自定义兼容 `baseUrl`：`chat`
- `doubao` / `dashscope`：`responses`
- 其他 provider：`chat`

当前 `responses` 只在这些 provider 上可用：

- `openai`
- `doubao`
- `dashscope`

历史配置里如果仍保存的是 `auto`，CLI 会在加载时归一化成显式协议，但新版本不再对用户暴露 `auto`。

---

## 5. 高频命令

### 5.1 Provider / Model

当前高频命令包括：

- `/provider`、`/providers`
- `/provider add`、`/provider edit`
- `/provider default`
- `/model`
- `/stream`
- `/skills`
- `/mcp`
- `/sessions`
- `/history`
- `/tree`
- `/events`
- `/replay`
- `/team`
- `/processes`

### 5.2 Session / Process

持续会话里最常用的两组命令是：

- `/resume`、`/load`、`/fork`
- `/process status|follow|logs|write|stop`

完整语法见 [命令参考](/docs/coding-agent/command-reference)。

---

## 6. TUI 交互约定

- `/`：打开 slash command 列表
- `Tab`：应用当前补全项
- `Ctrl+P`：打开 palette
- `Ctrl+R`：进入 replay
- `/team`：打开当前团队任务板
- `Enter`：提交输入
- `Esc`：中断当前任务，或关闭当前 UI 面板

palette 适合做两件事：

- 快速插入高频 slash command
- 在不记忆完整命令的情况下发现可用动作

`/team` 则适合在多智能体协作时快速查看：

- 当前有哪些 team tasks
- 每个 task 归属哪个成员
- 任务处于 `pending/running/completed/failed` 的哪一阶段
- 最近的团队协作消息是否已经发出

### 6.1 状态栏

当前常见状态包括：

- `Thinking`
- `Connecting`
- `Responding`
- `Working`
- `Retrying`
- `Waiting`
- `Stalled`

这些状态的核心作用是区分“模型在想”“工具在跑”“请求在重试”“看起来已经卡住”。

---

## 7. `/stream` 的真实语义

`/stream` 控制的是当前会话里的模型请求是否启用 `stream`。

不是纯粹的“文本渲染开关”。

### 7.1 命令

- `/stream`
- `/stream on`
- `/stream off`

### 7.2 行为

- `on`：后续请求使用流式输出，assistant / reasoning 增量到达
- `off`：后续请求走非流式，等待完整结果后再输出
- 作用域是当前 CLI/TUI 会话
- 不会把这一状态持久化成 workspace 配置

---

## 8. 一个推荐工作流

比较稳妥的顺序是：

1. 先用 one-shot 验证 provider / model / protocol 是否可用
2. 用 `/provider save` 或 `/provider add` 沉淀成全局 profile
3. 在目标仓库里用 `/provider use` 绑定当前 profile
4. 需要试验模型时，只对当前仓库使用 `/model <name>`
5. 长时间交互时切到 `tui`
6. 需要接 IDE 或自定义前端时切到 `acp`

这样做能避免把仓库临时试验污染成全局默认配置。

---

## 9. 继续阅读

1. [配置体系](/docs/coding-agent/configuration)
2. [Provider Profile 与模型切换](/docs/coding-agent/provider-profiles)
3. [会话、流式与进程](/docs/coding-agent/session-runtime)
4. [ACP 集成](/docs/coding-agent/acp-integration)
5. [命令参考](/docs/coding-agent/command-reference)
