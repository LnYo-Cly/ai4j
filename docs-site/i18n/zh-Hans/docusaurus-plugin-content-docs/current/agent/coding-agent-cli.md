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
- skill discovery：支持发现 workspace / global / 自定义目录中的 skills，并在会话内查看；
- model request streaming：支持在当前 CLI 会话里切换请求级 `stream=true|false`，并与 transcript 渲染保持一致。

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

skill 发现规则是：

- 默认扫描 `<workspace>/.ai4j/skills`
- 默认扫描 `~/.ai4j/skills`
- `skillDirectories` 中的相对路径按 workspace 根目录解析
- 会话内可用 `/skills` 列表查看，或用 `/skills <name>` 查看单个 skill 的元信息

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

### 5.5 skill 相关

- `/skills`：列出当前会话已发现的 skills、扫描 roots 和 workspace 配置位置
- `/skills <name>`：查看某个 skill 的来源、路径、描述和扫描 roots；只展示元信息，不回显 `SKILL.md` 正文

### 5.6 其他高频命令

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

`/stream` 控制的是当前 CLI 会话里的模型请求是否启用 `stream`，不是一个只影响 transcript 外观的渲染开关。

### 6.1 命令

- `/stream`
- `/stream on`
- `/stream off`

### 6.2 当前行为

- `/stream`：显示当前状态、作用域、请求级 `stream` 值和渲染行为
- `/stream on|off`：立即重建当前 session runtime，并切换后续请求的 `stream=true|false`
- `on`：provider 响应按增量到达，assistant / reasoning 文本按到达顺序增量写入 transcript
- `off`：provider 走非流式完成响应，assistant 文本以整理后的完成块呈现
- 作用域是当前 CLI 会话，不会把 `/stream` 持久化成 workspace 配置项

---

## 7. TUI 交互约定

当前 TUI shell 里，和 coding agent 直接相关的交互约定是：

- `/`：打开 slash command 列表
- `Tab`：应用当前补全项
- `Ctrl+P`：打开 command palette
- `Enter`：提交当前输入
- `Esc`：活跃 turn 时中断当前任务；空闲时关闭 slash palette 或清空输入

slash 命令补全当前已经覆盖：

- 根命令补全
- `/provider` 二级动作补全
- `/provider add|edit` 参数补全
- `/provider add|edit --protocol` 值补全
- `/model` 候选补全
- `/skills` 候选补全
- `/stream on|off` 候选补全

### 7.1 状态文案的当前含义

JLine 主缓冲区状态栏当前使用这些状态：

- `Thinking`：正在分析输入、工作区和工具上下文
- `Connecting`：正在打开模型请求或等待首个模型事件
- `Responding`：模型正在持续输出
- `Working`：当前主要在等待工具或进程结果
- `Retrying`：请求正在按当前重试策略重试
- `Waiting`：暂时没有新进展，但尚未判定为卡住
- `Stalled`：较长时间没有新进展，状态栏会明确提示 `press Esc to interrupt`

当前中断后的可见语义是：

- 已经显示到 transcript 的增量内容不会回滚
- 被中断的回合不会再补发最终完成块
- 壳层会输出 `Conversation interrupted by user.`，随后回到可继续输入的状态

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
