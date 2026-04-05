# 2026-03-26 Coding Agent CLI MCP 设计

## 背景

当前 `ai4j-cli` 已经具备 coding-agent CLI/TUI、session、provider profile、process 管理等能力，但 MCP 仍停留在 SDK 文档与独立接入层：

- `ai4j-cli` 还不能像 Codex / Claude Code 那样把 MCP 作为一等配置与运行时能力
- 现有 MCP 能力主要面向 SDK 使用者，不是面向 CLI 用户的交互产品
- CLI 缺少 “查看 / 添加 / 启用 / 暂停 / 删除 / 重试 MCP 服务” 的统一命令面
- CLI 当前没有把 MCP 生命周期与 session runtime rebinding 绑在一起

本轮目标已经明确：先把 `coding-agent-cli` 做成 MCP 的使用方与管理方，而不是 MCP Server。

## 核心结论

- `coding-agent-cli` 只做 MCP client / manager
- `coding-agent-cli` 不对外暴露自己，不作为 MCP server
- 第一阶段支持三种 transport：
  - `stdio`
  - `sse`
  - `streamable_http`
- 配置层级固定为：
  - 全局保存
  - workspace 引用
  - session 临时启停
- `/mcp` 只管理 MCP 服务，不做 tool 级白名单/黑名单交互
- MCP 配置变化立即重建当前 session runtime
- MCP 连接失败不阻塞 CLI 主体，只标记状态为 `error`

## 目标

- 让 `ai4j-cli` 读取并持久化 MCP 服务定义
- 支持 `~/.ai4j/mcp.json` 全局配置
- 支持 workspace 级启用 MCP 服务
- 支持 session 级 `pause/resume`
- 支持 `/mcp` 服务级命令：
  - `/mcp`
  - `/mcp list`
  - `/mcp add`
  - `/mcp edit`
  - `/mcp remove`
  - `/mcp enable`
  - `/mcp disable`
  - `/mcp pause`
  - `/mcp resume`
  - `/mcp retry`
- 在当前 session 中即时生效，而不是要求重启 CLI
- 为后续 Skills 体系复用同一套 global/workspace/session 模型打基础

## 非目标

- 本轮不做 tool 级 ACL
- 本轮不做 OAuth / browser login
- 本轮不做 MCP marketplace / 远端 catalog
- 本轮不做 CLI 自身 MCP Server 暴露
- 本轮不做多租户 user-scoped MCP client
- 本轮不做 Skills 本体
- 本轮不做 Agent / SubAgent / Plan 的 CLI 暴露

## 配置模型

### 1. 全局配置

位置：

- `%USERPROFILE%\\.ai4j\\mcp.json`

要求：

- 顶层格式与用户提供样例完全同构
- 顶层就是 `mcpServers`
- 不额外包一层 CLI 私有字段

结构示例：

```json
{
  "mcpServers": {
    "fetch": {
      "type": "sse",
      "url": "https://mcp.api-inference.modelscope.net/1e1a663049b340/sse"
    },
    "time": {
      "command": "uvx",
      "args": ["mcp-server-time"]
    },
    "bing-cn-mcp-server": {
      "type": "streamable_http",
      "url": "https://mcp.api-inference.modelscope.net/0904773a8c2045/mcp"
    }
  }
}
```

兼容规则：

- 若显式给出 `type`，按 `type` 解析
- 若未给 `type` 但存在 `command`，按 `stdio` 解析
- `http` 视为 `streamable_http` 的兼容别名

### 2. Workspace 配置

位置：

- `<workspace>/.ai4j/workspace.json`

新增字段：

```json
{
  "activeProfile": "zhipu-main",
  "modelOverride": "glm-4.7-plus",
  "enabledMcpServers": ["fetch", "time"]
}
```

职责：

- 只保存当前 workspace 启用了哪些全局 MCP 服务
- 不复制 transport/url/command 等服务定义
- `/mcp enable|disable` 改的就是这里

### 3. Session Override

session 级 override 不落盘，只在当前 CLI runtime 内存中保存：

- `pausedMcpServers`
- `retryRequestedServers`

职责：

- `/mcp pause|resume` 只作用于当前 session
- 不污染全局和 workspace 配置

## 命令语义

### `/mcp`

- 显示当前 effective MCP 服务列表
- 显示每个服务的：
  - 名称
  - transport
  - 状态
  - 是否来自 workspace enable
  - 最近错误摘要（若存在）

### `/mcp list`

- 列出所有全局定义的 MCP 服务
- 标记哪些已被当前 workspace 启用
- 标记哪些当前 session 处于 paused/error

### `/mcp add <name> ...`

新增全局 MCP 定义。

一期支持参数：

- `--type <stdio|sse|streamable_http>`
- `--url <url>`
- `--command <command>`
- `--args <arg1,arg2,...>` 或重复 `--arg`
- `--header <k=v>`
- `--env <k=v>`
- `--cwd <path>`

约束：

- `stdio` 需要 `command`
- `sse` / `streamable_http` 需要 `url`
- `time` 这类 stdio MCP 在 Windows 下默认按“直接进程”执行，不自动改写成 `cmd /c`

### `/mcp edit <name> ...`

- 编辑全局 MCP 定义
- 如果该服务当前有效，则立即重建当前 session runtime

### `/mcp remove <name>`

- 删除全局定义
- 同步从当前 workspace 的 `enabledMcpServers` 中移除
- 若当前 session 正在使用该服务，则立即重建 runtime

### `/mcp enable <name>`

- 将该服务加入当前 workspace `enabledMcpServers`
- 立即重建当前 session runtime

### `/mcp disable <name>`

- 将该服务从当前 workspace `enabledMcpServers` 中移除
- 同时清理当前 session 的 paused 状态
- 立即重建当前 session runtime

### `/mcp pause <name>`

- 仅把该服务加入当前 session `pausedMcpServers`
- 不修改 workspace 配置
- 立即重建当前 session runtime

### `/mcp resume <name>`

- 从当前 session `pausedMcpServers` 中移除
- 不修改 workspace 配置
- 立即重建当前 session runtime

### `/mcp retry <name>`

- 针对 `error` 状态手动重连
- 若重连成功，服务重新进入有效 runtime
- 若失败，保留 `error` 状态和最近错误信息

## 运行时架构

推荐实现路线：CLI 新增一层 MCP 解析与运行时管理，底层复用现有 SDK MCP 能力。

### 1. 配置解析层

新增对象：

- `CliMcpConfigManager`
- `CliResolvedMcpConfig`
- `CliResolvedMcpServer`

职责：

- 读取 `~/.ai4j/mcp.json`
- 读取 `<workspace>/.ai4j/workspace.json`
- 叠加当前 session override
- 生成当前 session 的 effective MCP 列表

### 2. 运行层

新增对象：

- `CliMcpRuntimeManager`
- `CliMcpConnectionHandle`
- `CliMcpStatusSnapshot`

职责：

- 为当前 CLI session 维护 MCP 连接与状态
- 将 effective MCP 列表转成 `McpClient` / `McpGateway`
- 收集状态：
  - `connected`
  - `connecting`
  - `paused`
  - `error`
  - `disabled`

### 3. 底层复用

直接复用现有 SDK 层：

- `McpServerConfig`
- `McpClient`
- `McpGateway`
- `TransportConfig`
- `McpTransportFactory`

这样做的原因：

- transport 与协议细节继续留在 SDK
- CLI 只负责 profile/workspace/session 语义
- 后续 Skills 也可以复用这套 runtime lifecycle

## Session Runtime Rebinding

MCP 变更必须立即作用到当前 session，因此不能只改字段。

切换流程：

1. 导出当前 `CodingSessionState`
2. 解析新的 effective MCP 列表
3. 关闭旧 `CliMcpRuntimeManager`
4. 建立新的 MCP runtime 与 tool registry
5. 用原 `sessionId` 和导出的 state 重建 session
6. 保留 transcript / memory / process 状态
7. 用新 runtime 继续当前会话

这样可以保证：

- session 连续
- memory 不丢
- provider/model/profile 逻辑与 MCP 逻辑一致
- `/mcp enable|disable|pause|resume` 语义稳定

## 错误处理

采用“失败不阻塞 CLI 主体”的策略：

- MCP 连接失败时：
  - CLI 启动继续
  - 服务状态记为 `error`
  - 最近错误摘要可在 `/mcp` 里查看
- 当前有效工具只注入成功连接的 MCP 服务
- `error` MCP 不拖垮其他 MCP，也不拖垮主会话

## Transport 规则

### `stdio`

- 直接执行 `command + args`
- Windows 下不自动改写为 `cmd /c`
- 只有配置本身显式使用 shell 包装时，才按 shell 处理

### `sse`

- 使用 `url`
- 支持 `headers`

### `streamable_http`

- 使用 `url`
- 支持 `headers`

## Slash Completion 与 TUI 展示

slash palette 需要新增：

- `/mcp`
- `/mcp list`
- `/mcp add`
- `/mcp edit`
- `/mcp remove`
- `/mcp enable`
- `/mcp disable`
- `/mcp pause`
- `/mcp resume`
- `/mcp retry`
- MCP 服务名候选
- `type` 值候选

TUI 顶部状态建议增加简短聚合：

- `mcp: 2 ok / 1 error`

但一期不单独做复杂 MCP 面板，只在 transcript + palette + `/mcp` 文本输出里呈现。

## 验收样例

第一阶段至少覆盖这 3 个 fixture：

```json
{
  "mcpServers": {
    "fetch": {
      "type": "sse",
      "url": "https://mcp.api-inference.modelscope.net/1e1a663049b340/sse"
    },
    "time": {
      "command": "uvx",
      "args": ["mcp-server-time"]
    },
    "bing-cn-mcp-server": {
      "type": "streamable_http",
      "url": "https://mcp.api-inference.modelscope.net/0904773a8c2045/mcp"
    }
  }
}
```

验收点：

- 配置可正确读取
- `time` 在 Windows 下按直接进程解析，不强改 `cmd /c`
- `/mcp enable|disable|pause|resume|retry` 可执行
- MCP 变更后当前 session 继续可用
- 某个 MCP 失败不阻塞整个 CLI

## 风险点

- 现有 `McpGateway` 偏全局平台化用法，CLI 需要限制为 session 内私有实例，避免全局污染
- `stdio` 类 MCP 在 Windows 下对 PATH 与环境依赖更敏感，需要把错误摘要做清楚
- 一期只做服务级控制，不做 tool 级控制；如果后续要补 ACL，需要在 runtime manager 里预留扩展点
- `/mcp add` 参数设计如果一步做太大，会拖慢实现；建议先保证三类 transport 跑通，再逐步扩字段

## 当前结论

本方案采用“CLI 增加 MCP config/runtime 管理层，底层复用 SDK MCP client/gateway”的路线。这样可以尽快把 MCP 做成 `coding-agent-cli` 的一等能力，同时又不把 MCP transport 细节硬编码进 CLI。该方案还会为后续 Skills 与 Agent / SubAgent / Plan 的 CLI 体系提供统一的 global/workspace/session 基础模型。
