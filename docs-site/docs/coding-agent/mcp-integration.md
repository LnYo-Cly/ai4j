---
sidebar_position: 8
---

# MCP 对接

这页不讲 MCP 协议本身，而是讲它在 `Coding Agent` 里怎样变成“活的工具面”。

因为在当前实现里，MCP 不是一份静态配置，也不是简单的 JSON 转发。

它要经过：

- 全局定义
- workspace 启用选择
- session 级暂停状态
- 连接建立
- tool 列表拉取
- 命名冲突校验
- registry / executor 重建

最后才会真的进入 agent 可用工具集。

---

## 1. 先看真实装配链

把 CLI 路径压成一条执行链：

```text
/mcp add|enable|pause...
  -> CliMcpConfigManager
  -> CliResolvedMcpConfig
  -> CliMcpRuntimeManager.initialize(...).start()
  -> 连接各个 MCP server
  -> listTools()
  -> validateToolNames(...)
  -> convertTools(...)
  -> StaticToolRegistry + ToolExecutor
  -> DefaultCodingCliAgentFactory.attachMcpRuntime(...)
  -> CodingAgentBuilder.toolRegistry/toolExecutor(...)
```

这里最关键的判断是：

`MCP` 在 Coding Agent 里并不是“协议名词”，而是一个真实的外部工具 runtime。

它有连接态、有错误态、有暂停态，也会因为命名冲突而整体失效。

---

## 2. 配置为什么分成两层

当前 CLI 路径把 MCP 配置拆成两部分：

### 2.1 全局定义

路径：

```text
~/.ai4j/mcp.json
```

它存的是 server definition，也就是“这个 MCP server 是谁、怎么连”。

当前 `CliMcpServerDefinition` 支持的核心字段包括：

- `type`
- `url`
- `command`
- `args`
- `env`
- `cwd`
- `headers`

### 2.2 工作区启用状态

路径：

```text
<workspace>/.ai4j/workspace.json
```

这里真正关心的是：

- `enabledMcpServers`

它回答的是另一个问题：

- 这个仓库当前要启用哪些全局已定义的 MCP server

这两层拆分的价值很大：

- 你可以在机器级维护一套稳定 server 定义
- 但每个 repo 只开启自己真正需要的那几项
- 不会把“存在于机器上”和“本仓真的要启用”混成一个概念

---

## 3. `CliMcpConfigManager` 真正负责什么

`CliMcpConfigManager` 不只是读写 JSON。

它还负责：

- 归一化 server 名称和字段
- 把 `http` 统一映射成 `streamable_http`
- 当缺省 `type` 且有 `command` 时，自动补成 `stdio`
- 校验 `stdio` 是否有 `command`
- 校验 `sse` / `streamable_http` 是否有 `url`
- 把全局定义和 workspace 启用状态解析成 `CliResolvedMcpConfig`

所以它输出的不是“原始配置”，而是“当前 session 可据此启动 runtime 的解析结果”。

这就是 `CliResolvedMcpConfig` 和 `CliResolvedMcpServer` 存在的原因。

---

## 4. runtime 启动时到底会发生什么

`CliMcpRuntimeManager.start()` 是整条链真正进入运行态的地方。

它会对每个 resolved server 按顺序做判断：

1. workspace 没启用，标成 `disabled`
2. 当前 session 被暂停，标成 `paused`
3. 配置不合法，标成 `error`
4. 否则尝试创建 client session 并连接
5. 拉取 `listTools()`
6. 校验工具名
7. 转换成 OpenAI function tool 形状
8. 把 server 状态设成 `connected`

如果 workspace 引用了一个根本不存在于全局定义里的名字，还会额外生成：

- `missing`

所以当前可见状态至少有五种：

- `connected`
- `disabled`
- `paused`
- `error`
- `missing`

这也是为什么 `/mcp` 输出不是单纯列配置，而是列“当前运行视图”。

---

## 5. MCP tool 为什么比 skill 严格得多

`CliMcpRuntimeManager.validateToolNames(...)` 会做三层冲突校验：

### 5.1 不能撞 built-in tools

这些名字是保留的：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

如果某个 MCP server 返回了同名 tool，当前 server 会直接进入错误态。

### 5.2 同一个 server 内不能重复

一个 server 如果返回了重复 tool name，也会直接报错。

### 5.3 不同 server 之间不能重名

第一个 server claim 了某个 tool name 后，后面的 server 再返回同名 tool，也会被视为冲突。

这里和 skill 的处理方式完全不同。

- skill 冲突是提示层遮蔽
- MCP 冲突是执行层失败

因为一旦执行层名字不唯一，tool routing 会直接失去确定性。

---

## 6. 三种 transport 在当前实现里的真实语义

核心 transport 仍然是三类：

- `stdio`
- `sse`
- `streamable_http`

适用面大致可以这样理解：

### `stdio`

本地进程式 MCP server。

最适合：

- 本地二进制
- Node/Python 启动脚本
- 需要直接由 CLI 拉起的 server

### `sse`

远端事件流式 MCP endpoint。

最适合：

- 已经在线上的服务
- 需要通过 SSE 保持消息流的对接方式

### `streamable_http`

标准 HTTP 形式的 MCP endpoint。

最适合：

- 已有 HTTP 化 MCP 服务
- 希望走更标准、更容易穿透代理的部署方式

一个容易混淆的点是：

- CLI 命令层仍然让你写 `--transport http`
- 但 `CliMcpConfigManager.normalizeTransportType(...)` 会把它归一化成 `streamable_http`
- `McpTransportFactory` 也把历史 `http` 作为兼容别名处理

所以：

- CLI 输入里可以继续写 `http`
- 配置文件和 ACP 注入里更推荐直接写 `streamable_http`

---

## 7. `/mcp` 系列命令各自改的是哪一层

CLI 里的 `/mcp` 命令本质上操作的是三层不同状态。

### 7.1 `add` / `remove`

操作全局定义层。

影响文件：

- `~/.ai4j/mcp.json`

`/mcp add --transport <...> <name> <target>` 只是在全局 store 里创建一个 server definition。

它不会自动代表当前 workspace 已启用。

### 7.2 `enable` / `disable`

操作 workspace 启用层。

影响文件：

- `<workspace>/.ai4j/workspace.json`

也就是改 `enabledMcpServers`。

一旦启用或禁用，当前 session runtime 会重建。

### 7.3 `pause` / `resume` / `retry`

操作 session 运行层。

这里最容易误解：

- `pause` 不会改全局 store
- `pause` 也不会改 workspace.json
- 它只是改当前会话内存里的 `pausedMcpServers`

然后通过 `switchSessionRuntime(...)` 重建当前 session 所用的 MCP runtime。

`retry` 也是同理，本质不是“对单连接做热修复”，而是让当前 session 重新走一遍 runtime 装配。

---

## 8. MCP tools 是怎样接进 Coding Agent 的

`DefaultCodingCliAgentFactory.prepareMcpRuntime(...)` 会先创建 `CliMcpRuntimeManager`。

如果 runtime 里真的产出了：

- `toolRegistry`
- `toolExecutor`

那么 `attachMcpRuntime(...)` 就会把它们塞给 `CodingAgentBuilder`。

也就是说当前 MCP 接入方式不是“built-in 内部隐藏分支”，而是：

- 先在 CLI 宿主层准备一个独立 runtime
- 再把它作为外部 tool surface 注入 `CodingAgentBuilder`

然后由 `CodingAgentBuilder.mergeToolRegistry(...)` / `mergeToolExecutor(...)` 与 built-ins、subagent tools 一起合并。

这也是为什么：

- MCP 本质上属于宿主装配层
- 不是 `ai4j-agent` 内核里写死的一部分

---

## 9. 错误和失败路径怎么判断

当前最常见的失败路径有五类。

### 9.1 workspace 引用了未定义 server

现象：

- `/mcp` 里看到 `state=missing`

含义：

- `workspace.json` 的 `enabledMcpServers` 里有名字
- 但 `~/.ai4j/mcp.json` 里找不到对应 definition

### 9.2 配置字段不完整

现象：

- `state=error`
- error 里通常会看到 `stdio transport requires command` 或 `<type> transport requires url`

### 9.3 连接或 `listTools()` 失败

现象：

- `state=error`
- 启动时还可能收到 `Warning: MCP unavailable: ...`

### 9.4 tool 名冲突

现象：

- 某个 server 直接进入 `error`
- 常见报错包括 built-in 冲突、同 server 重复、跨 server 重复

### 9.5 当前 session 暂停了 server

现象：

- `state=paused`
- workspace 仍然是 enabled
- 只是这一轮 session runtime 没把它接进可用工具集

---

## 10. ACP 下的 MCP 为什么又是另一条链

ACP 不一定依赖本机的 `~/.ai4j/mcp.json`。

`AcpJsonRpcServer.createSession(...)` 在处理 `session/new` / `session/load` 时，可以直接从请求参数里接收 `mcpServers`。

随后它会调用自己的 `resolveMcpConfig(...)`：

- 把每个传入 server 直接转成 `CliResolvedMcpServer`
- 默认都视为 workspace enabled
- 不走本地全局 store
- 不走 workspace.json 的 `enabledMcpServers`

这条链适合：

- IDE 插件动态注入 MCP
- 桌面宿主按项目临时分配 MCP
- 多租户宿主不希望依赖用户本地全局配置

所以 ACP 下的 MCP 更像：

- host-managed session-scoped MCP config

而 CLI 下的 MCP 更像：

- machine-scoped definition + workspace-scoped enablement

---

## 11. `/mcp` 输出应该怎么读

`CodingCliSessionRunner.renderMcpOutput()` 当前会把每个 server 打印成：

- `name`
- `type`
- `state`
- `workspace=enabled|disabled`
- `paused=yes|no`
- `tools=<count>`
- `error=<summary>`（有错时）

末尾还会附上：

- `store=<globalMcpPath>`
- `workspaceConfig=<workspaceConfigPath>`

这两个路径很重要，因为它们能直接告诉你问题到底落在：

- 全局定义层
- workspace 启用层
- 还是 runtime 连接层

---

## 12. 推荐的组织方式

如果你希望长期稳定使用 MCP，这样组织最稳：

1. 把稳定 server definition 放进 `~/.ai4j/mcp.json`
2. 只在真正需要的 repo 里启用对应名字
3. 会话级临时停用，用 `/mcp pause`
4. 遇到配置或连接修改，用 `retry` 或重新切换 session runtime
5. 宿主侧临时注入则走 ACP `mcpServers`

不太建议的做法是：

- 让所有 server 永久都在 workspace 启用
- 用同名 tool 在多个 server 间“碰运气”
- 把 `pause` 当持久化配置开关

---

## 13. 扩展和排障时优先看哪里

最值得直接读的入口类：

- `ai4j-cli/.../mcp/CliMcpConfigManager`
- `ai4j-cli/.../mcp/CliMcpRuntimeManager`
- `ai4j-cli/.../factory/DefaultCodingCliAgentFactory`
- `ai4j-cli/.../runtime/CodingCliSessionRunner`
- `ai4j-cli/.../acp/AcpJsonRpcServer`
- `ai4j/.../mcp/transport/McpTransportFactory`

推荐排障顺序：

1. `/mcp` 看状态是不是 `missing / error / paused`
2. 看 `store` 和 `workspaceConfig` 具体是哪个文件
3. 检查 transport 是否被归一化成预期类型
4. 检查 tool name 是否与 built-ins 或其他 server 冲突
5. 如果是 ACP，确认 `mcpServers` 是不是宿主按会话正确传入

---

## 14. 这页最该记住的结论

- Coding Agent 里的 MCP 是活的 runtime，不是静态配置片段
- 全局 `mcp.json` 负责“定义 server”，workspace.json 负责“这个仓库启用谁”
- `pause/resume` 是 session 级状态，不会持久化到配置文件
- tool 名冲突会让 MCP server 直接进错误态，而不是像 skill 那样静默遮蔽
- CLI 的 `http` 只是兼容输入，最终会归一化成 `streamable_http`
- ACP 可以绕过本地全局 store，按会话直接注入 MCP server

---

## 15. 继续阅读

1. [MCP 与 ACP](/docs/coding-agent/mcp-and-acp)
2. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
3. [ACP 集成](/docs/coding-agent/acp-integration)
4. [MCP 总览](/docs/mcp/overview)
