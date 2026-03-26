---
sidebar_position: 2
---

# Coding Agent CLI 与 TUI

本文档说明 `ai4j-cli` 当前可用的 coding-agent CLI/TUI 能力，重点覆盖：

- 启动方式与协议选择
- provider profile 与 workspace override
- 交互命令与 TUI 行为
- 当前实现边界

---

## 1. 这套 CLI 现在能做什么

`ai4j-cli` 现在已经不是一个只支持单次 prompt 的壳层，而是一个可持续会话的 coding-agent 入口，当前能力包括：

- one-shot 模式：直接带 `--prompt` 执行一次任务；
- interactive 模式：进入持续会话，保留 memory / session / process 状态；
- JLine TUI shell：支持 slash command、命令补全、palette、主缓冲区增量输出；
- session 持久化：支持保存、恢复、fork、history/tree/events/replay；
- process 管理：支持查看活跃进程、读取日志、向 stdin 写入、停止进程；
- provider profile：支持全局保存、workspace 引用、运行时热切换；
- model override：支持 workspace 级模型覆盖与即时切换；
- transcript streaming：支持在主缓冲区中按顺序增量显示 reasoning / assistant 文本。

---

## 2. 启动方式

### 2.1 one-shot

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Read README and summarize the project structure"
```

### 2.2 interactive CLI

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar code `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

### 2.3 TUI shell

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.0.0-jar-with-dependencies.jar tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

`code` 和 `tui` 使用同一套 coding-agent 会话能力，区别主要在交互壳层：

- `code`：默认走普通 CLI / REPL；
- `tui`：进入 JLine 驱动的 richer shell，支持 slash palette、按键补全和更完整的 transcript 管理。

---

## 3. 协议选择：现在只有 `chat` 和 `responses`

当前 `ai4j-cli` 对用户只暴露两种协议：

- `chat`
- `responses`

显式参数为：

```text
--protocol <chat|responses>
```

### 3.1 省略 `--protocol` 时的默认规则

如果不传 `--protocol`，CLI 会按 provider/baseUrl 在本地推导默认协议：

- `openai` + 官方 OpenAI host -> `responses`
- `openai` + 自定义兼容 `baseUrl` -> `chat`
- `doubao` / `dashscope` -> `responses`
- 其他 provider -> `chat`

这是一套本地路由规则，不是远端 capability probe。

### 3.2 兼容旧配置

历史配置里如果还保存了 `auto`：

- 新 CLI 不再接受用户显式传 `--protocol auto`
- 旧的 `providers.json` 中若存在 `auto`，会在加载时自动归一化成显式协议并写回配置文件

### 3.3 当前 `responses` 支持范围

当前 `ai4j-cli` 内部只对以下 provider 开启了 `responses`：

- `openai`
- `doubao`
- `dashscope`

如果对其他 provider 显式设置 `responses`，CLI 会直接报错，而不是模糊回退。

---

## 4. provider profile 与 workspace 配置

当前 CLI 配置分成两层：

- 全局 profile：`~/.ai4j/providers.json`
- workspace 配置：`<workspace>/.ai4j/workspace.json`

### 4.1 `providers.json`

全局 profile 用来保存可复用的 runtime 配置，例如：

```json
{
  "defaultProfile": "zhipu-main",
  "profiles": {
    "zhipu-main": {
      "provider": "zhipu",
      "protocol": "chat",
      "model": "glm-4.7",
      "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
      "apiKey": "env-or-stored-key"
    }
  }
}
```

### 4.2 `workspace.json`

workspace 层负责保存当前工作区引用的 profile 和模型覆盖，例如：

```json
{
  "activeProfile": "zhipu-main",
  "modelOverride": "glm-4.7-plus"
}
```

当前解析顺序是：

1. CLI 显式参数
2. workspace 配置
3. active profile
4. default profile
5. 环境变量 / system properties
6. 内建默认值

这样做的目的，是让“全局保存、workspace 引用、局部覆盖”这三层职责分离。

---

## 5. 当前常用命令

### 5.1 provider 相关

- `/providers`：列出已保存 profiles
- `/provider`：显示当前有效 provider / profile / protocol / model
- `/provider use <name>`：切换 workspace 当前使用的 profile，并立即重建当前 session runtime
- `/provider save <name>`：把当前运行中的 provider/protocol/model/baseUrl/apiKey 保存成 profile
- `/provider default <name|clear>`：设置或清除全局默认 profile
- `/provider remove <name>`：删除 profile

### 5.2 新增：`/provider add`

使用显式参数新建 profile：

```text
/provider add <profile-name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]
```

示例：

```text
/provider add zhipu-main --provider zhipu --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4
```

如果不传 `--protocol`，CLI 会按当前 provider/baseUrl 推导默认协议，并保存为显式值。

### 5.3 新增：`/provider edit`

更新已有 profile：

```text
/provider edit <profile-name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]
```

示例：

```text
/provider edit zhipu-main --model glm-4.7-plus
/provider edit openai-main --protocol responses
/provider edit zhipu-main --clear-api-key
```

如果被修改的是当前 effective profile，CLI 会立即重建当前 session runtime。

### 5.4 model 相关

- `/model`：显示当前 effective model 和 workspace override 状态
- `/model <name>`：保存 workspace model override，并立即切换当前 session runtime
- `/model reset`：清空 workspace model override，回退到 profile model

### 5.5 其他高频命令

- `/save`
- `/status`
- `/session`
- `/sessions`
- `/resume <id>`
- `/fork ...`
- `/history`
- `/tree`
- `/events`
- `/replay`
- `/compacts`
- `/processes`
- `/process status|follow|logs|write|stop ...`

---

## 6. `/stream` 的真实语义

`/stream` 控制的是 transcript 在交互壳层中的显示方式，不是协议切换命令。

### 6.1 命令

- `/stream`
- `/stream on`
- `/stream off`

### 6.2 当前行为

- `on`：assistant 文本和 reasoning 文本会按到达顺序增量写入主缓冲区 transcript
- `off`：等待本轮结果整理完成后再输出最终内容

当前页面里的 `/stream` 语义应理解为：

- “是否在交互 transcript 中增量渲染”
- 不是 “是否把 provider 请求参数改成 chat/responses”

---

## 7. TUI 交互约定

当前 TUI shell 里，和 coding agent 直接相关的交互约定是：

- `/`：打开 slash command 列表
- `Tab`：应用当前补全项
- `Ctrl+P`：打开 command palette
- `Enter`：提交当前输入
- `Esc`：清空输入

slash 命令补全当前已经覆盖：

- 根命令补全
- `/provider` 二级动作补全
- `/provider add|edit` 参数补全
- `/provider add|edit --protocol` 值补全
- `/model` 候选补全
- `/stream on|off` 候选补全

---

## 8. 当前边界

当前这套 coding-agent CLI 已经可以稳定使用，但还需要明确几个边界：

- provider 默认协议是本地规则推导，不是在线探测
- `/model` 的候选来源于本地 runtime/config，不会实时拉取远端官方模型目录
- `responses` 还不是所有 provider 都支持
- profile 配置支持全局保存和 workspace 引用，但还没有做更复杂的多 workspace 同步治理

---

## 9. 建议阅读顺序

如果你是第一次接触 AI4J 的 coding agent，建议按这个顺序阅读：

1. 本文：`Coding Agent CLI 与 TUI`
2. `Runtime 实现详解`
3. `CodeAct Runtime`
4. `Model Client 选择与适配`
5. `Memory 管理`
6. `Trace 可观测`

