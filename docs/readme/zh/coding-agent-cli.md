# Coding Agent CLI / TUI

[返回中文 README](../../../README.md) · [English README](../../../README-EN.md)

## Coding Agent CLI / TUI

AI4J 目前已经内置 `ai4j-cli`，可以直接作为本地 coding agent 使用，支持：

+ one-shot 与持续会话
+ CLI / TUI 两种交互模式
+ provider profile 持久化
+ workspace 级 model override
+ subagent 与 agent teams 协作
+ session 持久化、resume、fork、history、tree、events、replay
+ team board、team messages、team resume 等协作观测能力
+ process 管理与日志查看

### 安装

```bash
curl -fsSL https://lnyo-cly.github.io/ai4j/install.sh | sh
```

```powershell
irm https://lnyo-cly.github.io/ai4j/install.ps1 | iex
```

安装脚本会从 Maven Central 下载 `ai4j-cli` 并生成 `ai4j` 命令，前提是本机已经安装 Java 8+。

### one-shot 示例

```powershell
ai4j code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Read README and summarize the project structure"
```

### 交互式 CLI 示例

```powershell
ai4j code `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

### TUI 示例

```powershell
ai4j tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

### ACP 示例

```powershell
ai4j acp `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --workspace .
```

### 源码构建（可选）

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

产物示例：

```text
ai4j-cli/target/ai4j-cli-<version>-jar-with-dependencies.jar
```

如果你需要直接运行本地构建产物：

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-<version>-jar-with-dependencies.jar code --help
```

### 当前协议规则

当前 CLI 对用户只暴露两种协议：

+ `chat`
+ `responses`

如果省略 `--protocol`，会按 provider/baseUrl 在本地推导默认值：

+ `openai` + 官方 OpenAI host -> `responses`
+ `openai` + 自定义兼容 `baseUrl` -> `chat`
+ `doubao` / `dashscope` -> `responses`
+ 其他 provider -> `chat`

注意：

+ 不再对用户暴露 `auto`
+ 旧配置中的 `auto` 会在读取时自动归一化为显式协议

### provider profile 配置位置

+ 全局配置：`~/.ai4j/providers.json`
+ 工作区配置：`<workspace>/.ai4j/workspace.json`

推荐工作流：

+ 全局保存长期可复用 profile
+ workspace 只引用当前 activeProfile
+ 临时切模型时使用 workspace 的 `modelOverride`

`workspace.json` 也可以显式挂载额外 skill 目录：

```json
{
  "activeProfile": "openai-main",
  "modelOverride": "gpt-5-mini",
  "enabledMcpServers": ["fetch"],
  "skillDirectories": [
    ".ai4j/skills",
    "C:/skills/team",
    "../shared-skills"
  ]
}
```

skill 发现规则：

+ 默认扫描 `<workspace>/.ai4j/skills`
+ 默认扫描 `~/.ai4j/skills`
+ `skillDirectories` 中的相对路径按 workspace 根目录解析
+ 进入 CLI 后可用 `/skills` 查看当前发现到的 skill
+ 可用 `/skills <name>` 查看某个 skill 的路径、来源、描述和扫描 roots，不打印 `SKILL.md` 正文

### `/stream`、`Esc` 与状态提示

当前 `/stream` 的语义是“当前 CLI 会话里的模型请求是否启用 `stream`”，不是单纯的 transcript 渲染开关：

+ 作用域是当前 CLI 会话
+ `/stream on|off` 会切换请求级 `stream=true|false`，并立即重建当前 session runtime
+ `on` 时 provider 响应按增量到达，assistant 文本也按增量呈现
+ `off` 时等待完整响应后再输出整理后的完成块
+ 流式 event 粒度由上游 provider/SSE 决定，不保证“一个 event = 一个 token”
+ 如果通过 ACP/IDE 接入，宿主应按收到的 chunk 顺序渲染，并保留换行与空白

当前交互壳层里：

+ `Esc` 在活跃 turn 中断当前任务；空闲时关闭 palette 或清空输入
+ 状态栏会显示 `Thinking`、`Connecting`、`Responding`、`Working`、`Retrying`
+ 一段时间没有新进展会升级为 `Waiting`
+ 更久没有新进展会显示 `Stalled`，并提示 `press Esc to interrupt`

### 常用命令

+ `/providers`
+ `/provider`
+ `/provider use <name>`
+ `/provider save <name>`
+ `/provider add <name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]`
+ `/provider edit <name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]`
+ `/provider default <name|clear>`
+ `/provider remove <name>`
+ `/model`
+ `/model <name>`
+ `/model reset`
+ `/skills`
+ `/skills <name>`
+ `/stream [on|off]`
+ `/processes`
+ `/process status|follow|logs|write|stop ...`
+ `/resume <id>` / `/load <id>` / `/fork ...`

### 文档入口

+ [Coding Agent 总览](../../../docs-site/docs/coding-agent/overview.md)
+ [Coding Agent 快速开始](../../../docs-site/docs/coding-agent/quickstart.md)
+ [CLI / TUI 使用指南](../../../docs-site/docs/coding-agent/cli-and-tui.md)
+ [会话、流式与进程](../../../docs-site/docs/coding-agent/session-runtime.md)
+ [配置体系](../../../docs-site/docs/coding-agent/configuration.md)
+ [Tools 与审批机制](../../../docs-site/docs/coding-agent/tools-and-approvals.md)
+ [Skills 使用与组织](../../../docs-site/docs/coding-agent/skills.md)
+ [MCP 对接](../../../docs-site/docs/coding-agent/mcp-integration.md)
+ [ACP 集成](../../../docs-site/docs/coding-agent/acp-integration.md)
+ [TUI 定制与主题](../../../docs-site/docs/coding-agent/tui-customization.md)
+ [命令参考](../../../docs-site/docs/coding-agent/command-reference.md)

## 其它支持
+ [[低价中转平台] 低价ApiKey—限时特惠 ](https://api.trovebox.online/)
+ [[在线平台] 每日白嫖额度-所有模型均可使用 ](https://chat.trovebox.online/)
