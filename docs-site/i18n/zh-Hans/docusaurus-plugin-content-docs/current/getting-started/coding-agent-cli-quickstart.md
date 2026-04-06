---
sidebar_position: 6
---

# Coding Agent CLI 快速开始

如果你想最快体验 AI4J 的 coding agent，而不是从 SDK 代码接起，建议直接从 `ai4j-cli` 开始。

这条链路适合：

- 直接在本地代码仓里做问答、改文件、跑命令；
- 体验持续会话、session、process、slash command；
- 验证不同 provider / model / protocol 的切换行为。

---

## 1. 安装 `ai4j` 命令

推荐先通过文档站托管脚本安装 `ai4j-cli`。脚本会从 Maven Central 下载 `ai4j-cli` fat jar，并在本地生成 `ai4j` 命令。

### 1.1 macOS / Linux

```bash
curl -fsSL https://lnyo-cly.github.io/ai4j/install.sh | sh
```

### 1.2 Windows PowerShell

```powershell
irm https://lnyo-cly.github.io/ai4j/install.ps1 | iex
```

安装完成后建议先执行：

```bash
ai4j --help
```

同一套安装同时支持：

- `ai4j code`
- `ai4j tui`
- `ai4j acp`

---

## 2. 最小 one-shot 示例

```powershell
ai4j code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Read README and summarize the project structure"
```

适用场景：

- 单次任务
- 不需要持续会话
- 先验证 provider/model 配置是否能通

---

## 3. 进入交互式 coding session

```powershell
ai4j code `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

交互式会话下，你可以直接输入自然语言任务，也可以使用 slash 命令：

- `/status`
- `/session`
- `/providers`
- `/provider ...`
- `/model ...`
- `/skills [name]`
- `/stream on|off`

这里有两个关键语义：

- `/stream on|off` 切换的是当前 CLI 会话里的模型请求流式开关，并会立即重建当前 session runtime
- `/skills <name>` 只显示 skill 的来源、路径、描述和扫描 roots，不回显 `SKILL.md` 正文

---

## 4. 启动 TUI shell

```powershell
ai4j tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

当前 TUI shell 支持：

- `/` 打开命令面板
- `Tab` 应用补全
- `Ctrl+P` 打开 command palette
- `Enter` 提交输入
- `Esc` 在活跃 turn 中断当前任务；空闲时关闭 palette 或清空输入

状态栏会在 `Thinking / Connecting / Responding / Working / Retrying` 之间切换；如果一段时间没有新进展，会升级为 `Waiting`，再继续无进展会显示 `Stalled` 并提示可以按 `Esc` 中断。

---

## 5. 从源码打包运行（可选）

如果你正在开发 `ai4j-cli` 本身，或者想使用本地源码构建产物，也可以继续走 jar 启动方式：

```powershell
mvn -pl ai4j-cli -am -DskipTests package
java -jar .\ai4j-cli\target\ai4j-cli-<version>-jar-with-dependencies.jar code --help
```

---

## 6. 你接下来该看哪一页

如果你已经能跑起来，建议继续看：

1. `Agent / Coding Agent CLI 与 TUI`
2. `Agent / 多 Provider Profile 实战`
