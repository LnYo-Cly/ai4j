---
sidebar_position: 9
---

# ACP 集成

`ACP` 是 Coding Agent 面向宿主集成的标准入口。

如果你要把 `ai4j-cli` 接到 IDE、桌面应用或自定义前端，而不是直接使用终端交互，那么应当使用 `acp` 模式。

---

## 1. ACP 模式解决什么问题

它解决的是“宿主如何驱动一个本地 coding agent”。

也就是说，宿主不需要模拟终端输入输出，而是直接与 `ai4j-cli acp` 做结构化 JSON-RPC 通信。

适合：

- IDE 插件
- 桌面端壳层
- 自定义工作台
- 中间层代理进程

---

## 2. 启动方式

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.1.0-jar-with-dependencies.jar acp `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --workspace .
```

约定：

- `stdin/stdout`：换行分隔的 JSON-RPC
- `stderr`：日志、告警和诊断信息

---

## 3. `initialize`

最小初始化请求：

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
```

当前返回能力里可以重点关注：

- `loadSession=true`
- `sessionCapabilities.list`
- `mcpCapabilities.http=true`
- `mcpCapabilities.sse=true`
- `promptCapabilities.audio=false`
- `promptCapabilities.image=false`
- `promptCapabilities.embeddedContext=false`

也就是说，当前 ACP 首先是一个文本 prompt + 会话事件流的集成面。

---

## 4. 最小会话流程

### 4.1 `session/new`

```json
{"jsonrpc":"2.0","id":2,"method":"session/new","params":{"sessionId":"demo-session","cwd":"C:/workspaces/ai4j-sdk"}}
```

这里的 `cwd` 需要是绝对路径。

建会话成功后，除了 `id=2` 的正常响应外，服务端还会按 ACP 标准补发：

```json
{
  "jsonrpc": "2.0",
  "method": "session/update",
  "params": {
    "sessionId": "demo-session",
    "update": {
      "sessionUpdate": "available_commands_update",
      "availableCommands": [
        {"name": "status", "description": "Show current session status"}
      ]
    }
  }
}
```

这条事件的意义是：

- 告诉宿主“当前会话有哪些 slash commands”
- 让 IDE / 客户端在用户输入 `/` 时弹出命令面板
- 避免客户端自己硬编码一套命令列表

同样地，`session/load` 成功后也会发送一遍 `available_commands_update`。

### 4.2 `session/prompt`

```json
{"jsonrpc":"2.0","id":3,"method":"session/prompt","params":{"sessionId":"demo-session","prompt":[{"type":"text","text":"列出这个仓库的模块结构"}]}}
```

发送后会先收到 `session/update`，最后收到：

```json
{"jsonrpc":"2.0","id":3,"result":{"stopReason":"end_turn"}}
```

如果用户输入的是：

```text
/status
```

ACP 客户端通常仍然会把它作为普通 `session/prompt` 文本发过来。

当前 `ai4j-cli acp` 的处理方式是：

- 如果是 ACP 已知 slash command，就在本地执行
- 不再把这条命令透传给模型
- 执行结果仍然通过标准 `session/update` 文本事件返回

也就是说，ACP 下“命令发现”和“命令执行”是两件事：

- 发现：`available_commands_update`
- 执行：普通 `session/prompt`

其中 `/team`、`/team list|status|messages|resume`、`/experimental` 的执行结果也走这条路径，不会额外引入新的 ACP 自定义事件。宿主即使没有专门的“团队看板”或“runtime feature toggle”组件，也可以先把它们作为普通文本摘要展示。

这里要特别区分两种 `/team` 语义：

- `/team`：读取当前 session event ledger，返回“当前会话视角”的 team board
- `/team status|messages|resume`：读取 `<workspace>/.ai4j/teams` 下的持久化 team snapshot / mailbox

其中 `/team resume <team-id>` 在 ACP 里仍然只是返回一个持久化快照视图，不表示 ACP 服务端重新拉起了某个 team runtime。

---

## 5. 会话管理方法

### 5.1 `session/list`

```json
{"jsonrpc":"2.0","id":4,"method":"session/list","params":{"cwd":"C:/workspaces/ai4j-sdk"}}
```

用于枚举某个工作区下已有的 session。

### 5.2 `session/load`

```json
{"jsonrpc":"2.0","id":5,"method":"session/load","params":{"sessionId":"demo-session","cwd":"C:/workspaces/ai4j-sdk"}}
```

加载成功后，服务端会回放历史 `session/update`。

### 5.3 `session/cancel`

```json
{"jsonrpc":"2.0","method":"session/cancel","params":{"sessionId":"demo-session"}}
```

适合：

- 宿主侧“停止生成”
- 正在等待工具审批时统一取消
- 终止当前活跃 turn

---

## 6. 事件模型

宿主最常消费的是 `session/update`。

常见类型：

- `available_commands_update`
- `user_message_chunk`
- `agent_thought_chunk`
- `agent_message_chunk`
- `tool_call`
- `tool_call_update`

这些事件有两个来源，但语义保持一致：

- live turn：模型和工具实时产生的增量
- history replay：`session/load` 后对历史事件的回放

需要补充一个例外：

- `available_commands_update` 不是模型生成事件，而是会话元数据事件
- 它通常出现在 `session/new` / `session/load` 之后
- 宿主应缓存这份命令清单，并在命令面板或 slash menu 中复用

宿主实现时，按收到顺序消费即可，不需要为“历史事件”和“实时事件”写两套渲染器。

### 6.1 Team / SubAgent 任务在 ACP 里的映射

`ai4j-cli acp` 不会为团队协作再发一套自定义协议事件。

当前做法是继续遵守 ACP 标准：

- 任务创建 -> `tool_call`
- 任务更新 -> `tool_call_update`
- 团队消息 -> 也映射成 `tool_call_update`

其中 Team task 的 `rawInput / rawOutput` 会补更多结构化字段，例如：

- `memberId`
- `memberName`
- `phase`
- `percent`
- `heartbeatCount`
- `durationMillis`

所以 ACP 宿主如果只想做最小接入，只消费：

- `sessionUpdate`
- `toolCallId`
- `status`
- `content`

就足够。

如果想做更强的 IDE 可视化，则可以继续读取 `rawInput / rawOutput`，把团队任务渲染成：

- 成员 lane
- 任务进度标签
- heartbeat / reassign / release 状态提示

而不需要协议层扩展。

一个典型文本事件如下：

```json
{
  "jsonrpc": "2.0",
  "method": "session/update",
  "params": {
    "sessionId": "demo-session",
    "update": {
      "sessionUpdate": "agent_message_chunk",
      "content": {
        "type": "text",
        "text": "项目包含 ai4j、ai4j-cli、ai4j-agent 等模块。"
      }
    }
  }
}
```

---

## 7. 流式文本语义

ACP 当前对文本增量的处理方式非常直接：

- 推理增量直接发 `agent_thought_chunk`
- 回复增量直接发 `agent_message_chunk`
- `content.text` 就是宿主应该顺序追加的字符串 chunk

要特别注意：

- 一个 event 不等于一个 token
- 一个 chunk 可能是一个字、一个词，也可能是一小段文本
- chunk 内部的换行与空白要原样保留
- 没必要在协议层再做一轮 coalesce

## 7.1 ACP slash commands 的当前范围

当前 ACP 默认暴露的是一组适合宿主集成的命令子集：

- `help`
- `status`
- `session`
- `save`
- `providers`
- `provider`
- `model`
- `skills`
- `agents`
- `mcp`
- `sessions`
- `history`
- `tree`
- `events`
- `team`
- `compacts`
- `checkpoint`
- `processes`
- `process`

这样做的原因是：

- ACP 更偏“结构化宿主集成”，不是完整终端替身
- 会保留一批适合 IDE 集成、且不依赖真实终端控件的高价值命令
- 命令面板应优先稳定、可预测，而不是和 TUI 命令集完全耦合

`team` 属于一个值得保留的高价值命令：

- 不依赖终端交互语义
- 返回结果是纯文本，所有 ACP 宿主都能安全消费
- 内容是 Team task / Team message 聚合后的当前任务板，可作为 lane UI 的文本回退

`provider` / `model` 现在也属于 ACP 暴露范围：

- 可以在 IDE 宿主中直接查看当前 provider/profile/model 状态
- 可以通过 `/provider use ...`、`/provider add ...`、`/model ...` 做会话级切换
- 切换后会立即重建当前 ACP session runtime，而不是只改配置文件不生效

如果你在 IDE 中输入 `/` 后没有立刻看到命令面板，优先排查：

1. 是否已经完成 `session/new` 或 `session/load`
2. 日志里是否收到了 `available_commands_update`
3. 宿主客户端是否实现了 ACP slash command UI

如果宿主想做更平滑的显示动画，可以在 UI 层缓冲；但协议语义本身就是“按顺序到达的字符串 chunk”。

---

## 8. 权限确认

如果使用 `--approval manual`，宿主会收到 `session/request_permission`。

当前支持的选项有：

- `allow_once`
- `allow_always`
- `reject_once`
- `reject_always`

宿主返回时，只需要带回最终选择结果，例如：

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "outcome": {
      "outcome": "selected",
      "optionId": "allow_once"
    }
  }
}
```

---

## 9. ACP 下的 MCP 注入

ACP 场景除了使用本地全局 MCP 配置外，还可以在建会话时直接传 `mcpServers`。

这适合：

- 宿主动态配置 server
- 每个会话临时挂不同 MCP
- 不依赖本地固定配置文件

示例：

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "session/new",
  "params": {
    "sessionId": "demo-session",
    "cwd": "C:/workspaces/ai4j-sdk",
    "mcpServers": [
      {
        "name": "fetch",
        "type": "sse",
        "url": "http://127.0.0.1:3101/sse"
      },
      {
        "name": "browser",
        "type": "stdio",
        "command": "npx",
        "args": ["-y", "@modelcontextprotocol/server-fetch"]
      }
    ]
  }
}
```

常见字段有：

- `name`
- `type`
- `url`
- `command`
- `args`
- `env`
- `cwd`
- `headers`

---

## 10. 宿主实现建议

- `stdout` 只作为协议通道，`stderr` 只作为日志通道
- 所有请求都使用换行分隔的 JSON-RPC
- `cwd` 传绝对路径，`sessionId` 保持稳定
- 统一按事件顺序渲染 `session/update`
- 文本 chunk 保留原始换行与空白
- 当前 ACP 以文本 prompt 为主，不要假设图片 / 音频输入已经可用

---

## 11. 继续阅读

1. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
2. [会话、流式与进程](/docs/coding-agent/session-runtime)
3. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
4. [命令参考](/docs/coding-agent/command-reference)

