---
sidebar_position: 4
---

# Coding Agent 命令参考手册

本文档只覆盖当前 `ai4j-cli` coding-agent 会话里已经实现的高频命令。

---

## 1. provider / model / stream

### `/providers`

列出已保存的 provider profiles。

```text
/providers
```

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

### `/stream`

显示 transcript streaming 状态。

```text
/stream
```

---

### `/stream on|off`

切换交互 transcript 的增量渲染行为。

```text
/stream on
/stream off
```

说明：

- `on`：assistant / reasoning 会增量写入主缓冲区 transcript
- `off`：等待一轮完成后再输出整理后的最终文本
- 这不是 provider 协议切换命令

---

## 2. session / history / compact

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

### `/resume`

恢复一个已保存 session。

```text
/resume <id>
/load <id>
```

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

### `/compacts`

查看最近 compact 历史。

```text
/compacts [n]
```

---

### `/compact`

对当前 session memory 进行压缩。

```text
/compact
/compact <summary>
```

---

## 3. process 管理

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

## 4. 其他命令

- `/help`
- `/theme [name]`
- `/commands`
- `/palette`
- `/cmd <name> [args]`
- `/checkpoint`
- `/clear`
- `/exit`
- `/quit`

---

## 5. 补全与交互约定

当前 TUI shell 下：

- `/`：打开命令面板
- `Tab`：应用当前补全项
- `Ctrl+P`：打开 command palette
- `Enter`：提交输入
- `Esc`：清空输入

当前命令补全已覆盖：

- 根命令
- `/provider` 二级动作
- `/provider add|edit` 参数
- `/provider add|edit --protocol` 值
- `/model` 候选
- `/stream on|off`

