---
sidebar_position: 2
---

# Coding Agent 快速开始

如果你想最快把 AI4J Coding Agent 跑起来，这一页只做一件事：给你最短路径。

---

## 1. 打包

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

产物：

```text
ai4j-cli/target/ai4j-cli-2.0.0-jar-with-dependencies.jar
```

---

## 2. 最小 one-shot

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Read README and summarize the project structure"
```

适合：

- 单次任务；
- 先验证 provider/model 是否能通；
- 不需要持续会话。

---

## 3. 持续会话

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar code `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

进入持续会话后，常用命令包括：

- `/provider`
- `/model`
- `/experimental`
- `/stream`
- `/skills`
- `/mcp`
- `/sessions`
- `/history`
- `/processes`

---

## 4. 启动 TUI

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

当前默认交互：

- `/` 打开 slash command 列表；
- `Tab` 接受补全；
- `Ctrl+P` 打开 palette；
- `Ctrl+R` 打开 replay；
- `Esc` 中断当前任务或关闭面板。

---

## 5. 启动 ACP

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar acp `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --workspace .
```

适合：

- 接 IDE；
- 接桌面端；
- 接自定义宿主。

当前 ACP 会在 `session/new` / `session/load` 后按标准发送：

- `available_commands_update`

宿主如果支持 ACP slash command 面板，通常会据此展示 `/` 命令列表。

当前 ACP 侧默认暴露的是一组“适合 headless 宿主”的命令子集，例如：

- `/help`
- `/status`
- `/session`
- `/save`
- `/experimental`
- `/skills`
- `/agents`
- `/mcp`
- `/sessions`
- `/history`
- `/tree`
- `/events`
- `/compacts`
- `/checkpoint`
- `/processes`
- `/process`

完整流程见 [ACP 集成](/docs/coding-agent/acp-integration)。

---

## 6. 推荐下一步

1. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
2. [发布、安装与 GitHub Release](/docs/coding-agent/release-and-installation)
3. [配置体系](/docs/coding-agent/configuration)
4. [Runtime 架构](/docs/coding-agent/runtime-architecture)
5. [Provider Profile 与模型切换](/docs/coding-agent/provider-profiles)
6. [会话、流式与进程](/docs/coding-agent/session-runtime)
7. [Compact 与 Checkpoint 机制](/docs/coding-agent/compact-and-checkpoint)
8. [Prompt 组装与上下文来源](/docs/coding-agent/prompt-assembly)
