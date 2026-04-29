---
sidebar_position: 2
---

# Coding Agent 快速开始

这页只做一件事：

- 让你用最短路径把 `ai4j-cli` 跑起来

但“最短路径”不应该只给一行命令，还要让你知道每个入口背后到底会发生什么，否则一旦和预期不一致，就不知道该从哪排查。

---

## 1. 先知道当前真正的三个入口

`Ai4jCli` 当前暴露的是三个命令面：

- `code`
- `tui`
- `acp`

而且它还有两个常被忽略的行为：

- 如果你直接写 `ai4j-cli --model ...`，默认按 `code` 处理
- `tui` 本质上只是给 `code` 额外补上 `--ui tui`

所以当前入口关系可以压成：

```text
ai4j-cli code ...   -> coding session CLI host
ai4j-cli tui ...    -> code --ui tui
ai4j-cli acp ...    -> ACP stdio server
```

这说明 `tui` 不是另一套独立 runtime，而是同一个 command 的另一种 host 模式。

---

## 2. 构建当前仓库里真正可直接运行的产物

构建命令：

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

当前最值得直接使用的产物是：

```text
ai4j-cli/target/ai4j-cli-2.3.0-jar-with-dependencies.jar
```

为什么是这个文件，而不是普通 jar：

- `ai4j-cli/pom.xml` 里通过 `maven-assembly-plugin` 生成 `jar-with-dependencies`
- manifest 主类是 `io.github.lnyocly.ai4j.cli.Ai4jCliMain`
- 这个产物把 CLI 依赖一并打进去，更适合直接 `java -jar`

所以当前“最快跑起来”的最稳路径就是：

- 先构建 fat jar
- 再直接 `java -jar`

---

## 3. 最小 one-shot

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.3.0-jar-with-dependencies.jar code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Read README and summarize the project structure"
```

这一条命令的关键不只是“带了 `--prompt`”，而是：

- `CodeCommand` 看到 `--prompt` 后会走 one-shot 交互形态
- 它不是进入持续 REPL
- 也不是 TUI

适合场景：

- 首次验证 provider / key / protocol / model 是否打通
- 做最小 smoke test
- 不需要复用 session

如果这一步都没跑通，先不要急着怀疑 session、MCP、skills、TUI。

优先排查：

- provider / api key
- protocol 是否与 provider 匹配
- model 名是否正确

---

## 4. 持续 CLI 会话

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.3.0-jar-with-dependencies.jar code `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

和 one-shot 的真正差别是：

- 这里没有传 `--prompt`
- `CodeCommand` 会进入持续交互模式

当前默认 session 语义也要一起记住：

- 默认会持久化 session
- 默认 session store 在 `<workspace>/.ai4j/sessions`
- 如果显式传 `--no-session`，才会改成 memory-only

所以持续 CLI 会话不是“纯内存 REPL”，而是一条带 session store 的工作流入口。

进入后最常用的命令通常是：

- `/provider`
- `/model`
- `/stream`
- `/skills`
- `/mcp`
- `/sessions`
- `/history`
- `/processes`

---

## 5. TUI 入口的最短路径

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.3.0-jar-with-dependencies.jar tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

这里最容易误解的是：

- `tui` 不是另一套命令集合
- 它本质是 `code --ui tui`

当前交互层面常见键位包括：

- `/` 打开 slash command 列表
- `Tab` 接受补全
- `Ctrl+P` 打开 palette
- `Ctrl+R` 打开 replay
- `Esc` 中断当前 turn 或关闭面板

但别把这些键位误解成“独立运行时能力”。

真正的 session、tool、approval、MCP、process 管理仍然是同一套 coding runtime 在工作。

---

## 6. ACP 入口的最短路径

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.3.0-jar-with-dependencies.jar acp `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --workspace .
```

这条命令启动的不是终端 REPL，而是 ACP stdio server。

当前约定是：

- `stdin/stdout` 走换行分隔 JSON-RPC
- `stderr` 只写日志和告警

而且它和 `code` 共用同一套 provider / model / workspace 解析规则。

这意味着：

- 你在 CLI 下能复现的 provider/protocol 问题，ACP 下通常也会一样复现
- ACP 不是绕开配置系统的特殊入口

---

## 7. 如果你不显式写 `--protocol`，默认值怎么来

快速开始里最容易踩的坑之一就是协议默认值。

当前本地规则是：

- `openai` 且 baseUrl 为空或走官方 host，默认更偏 `responses`
- `openai` 但使用自定义兼容 host，默认更偏 `chat`
- `doubao` / `dashscope` 默认更偏 `responses`
- 其他 provider 默认 `chat`

所以：

- 在 OpenAI 官方 host 下做 quickstart，最稳的是 `responses`
- 在 OpenAI-compatible host 下，不要想当然地继续套官方 OpenAI 默认

如果你不确定，最稳做法仍然是：

- 在 quickstart 阶段把 `--protocol` 写死

---

## 8. 第一次跑通后，最该立即验证什么

最小成功不代表“整套系统都正常”。

第一次跑通后，建议立刻做这几项确认：

### 8.1 session 是否真的落盘

看：

```text
<workspace>/.ai4j/sessions
```

### 8.2 provider / model 当前有效值是否如预期

在持续 CLI / TUI 里执行：

```text
/provider
/model
```

### 8.3 workspace 是否按你想的那个目录绑定

执行：

```text
/session
```

### 8.4 MCP 启动是否有告警

如果有 MCP 配置，启动时注意终端或 `stderr` 是否有：

- `Warning: MCP unavailable: ...`

---

## 9. 最容易踩坑的 6 个点

### 9.1 用普通 jar 而不是 fat jar

当前最稳的直接运行产物是 `jar-with-dependencies`。

### 9.2 误把 `tui` 当成另一套 agent

它只是 `code --ui tui` 的别名入口。

### 9.3 以为不传 `--prompt` 仍然是 one-shot

恰好相反，不传 `--prompt` 才会进入持续交互模式。

### 9.4 忘了 `--workspace`

虽然默认会用当前目录，但真实工作里最好显式写清楚，尤其是 ACP 和多仓环境。

### 9.5 在 OpenAI-compatible host 下沿用官方 OpenAI 协议预期

自定义 `baseUrl` 会影响默认协议选择。

### 9.6 以为 quickstart 成功就说明审批、MCP、session、team 都没问题

quickstart 只能证明最小主链打通，不能替代完整运行验证。

---

## 10. 建议的最短验证顺序

如果你是第一次接入，最稳的顺序是：

1. 先跑 one-shot，确认 provider / protocol / model 打通
2. 再跑持续 CLI，确认 session store、slash commands、workspace 绑定正常
3. 再跑 TUI，确认终端交互层是否满足你的使用方式
4. 最后再接 ACP、MCP、审批和实验性 subagent / team 能力

这样排障最清晰，因为你不会把所有变量一次性堆进第一轮验证。

---

## 11. 这页最该记住的结论

- 当前真正的运行入口是 `code`、`tui`、`acp`
- `tui` 只是 `code --ui tui` 的宿主别名，不是另一套 runtime
- quickstart 最稳的直接产物是 `ai4j-cli-<version>-jar-with-dependencies.jar`
- `--prompt` 决定 one-shot；不传 `--prompt` 才进入持续会话
- quickstart 跑通后，下一步应该验证 session、workspace、provider/model 状态，而不是立刻堆更多功能

---

## 12. 推荐下一步

1. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
2. [配置体系](/docs/coding-agent/configuration)
3. [会话、流式与进程](/docs/coding-agent/session-runtime)
4. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
5. [MCP 对接](/docs/coding-agent/mcp-integration)
6. [ACP 集成](/docs/coding-agent/acp-integration)
