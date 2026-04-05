# 2026-03-26 Coding Agent CLI MCP 实施计划

- 状态：Planned
- 优先级：P0
- 依赖设计：`docs/plans/2026-03-26-coding-agent-cli-mcp-design.md`
- 目标模块：`ai4j-cli`
- 复用模块：`ai4j` MCP client/gateway/transport

## 1. 实施目标

为 `ai4j-cli` 增加 MCP 一等能力，并保证：

- CLI 能读取 `~/.ai4j/mcp.json`
- workspace 能启用/禁用 MCP 服务
- session 能临时 `pause/resume`
- MCP 变更立即重建当前 session runtime
- MCP 失败不阻塞 CLI 主体

## 2. 阶段拆分

### Phase 1：配置模型与解析

目标：

- 建立 CLI 侧 MCP 配置读写能力
- 建立 global/workspace/session 的 effective 解析逻辑

主要改动：

- `CliWorkspaceConfig`
- `CliMcpConfigManager`（新增）
- `CliResolvedMcpConfig`（新增）
- `CliResolvedMcpServer`（新增）

验收：

- 可读取 `%USERPROFILE%\\.ai4j\\mcp.json`
- 顶层兼容 `mcpServers`
- workspace `enabledMcpServers` 可读写
- 未显式 `type` 且存在 `command` 时按 `stdio` 解析

### Phase 2：MCP runtime 管理

目标：

- 建立 session 私有的 MCP runtime lifecycle
- 把 MCP 注入 coding agent runtime

主要改动：

- `CliMcpRuntimeManager`（新增）
- `CliMcpConnectionHandle`（新增）
- `CliMcpStatusSnapshot`（新增）
- `DefaultCodingCliAgentFactory`
- `CodingCliSessionRunner`

验收：

- `stdio` / `sse` / `streamable_http` 三类 transport 可建立连接
- 失败服务进入 `error`，不阻塞 CLI
- 成功连接的 MCP 能进入当前 tool registry

### Phase 3：`/mcp` 命令面

目标：

- 提供 Codex / Claude Code 风格的服务级 MCP 管理命令

主要改动：

- `CodingCliSessionRunner`
- `SlashCommandController`

命令范围：

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

验收：

- 命令可执行
- 命令补全可用
- MCP 名称候选可用

### Phase 4：session runtime rebinding

目标：

- 将 MCP 变更与当前 session 生命周期绑定

主要改动：

- `CodingCliSessionRunner`
- `JlineCodeCommandRunner`

验收：

- `/mcp enable|disable|pause|resume|edit|remove` 后当前 session 不断
- memory / transcript / process 状态保留
- 新 MCP 配置对下一轮 agent 调用立即生效

### Phase 5：TUI 状态与自验证

目标：

- 在 TUI 中暴露最小可用 MCP 状态
- 完成 fixture 级验证与回归测试

主要改动：

- `JlineShellTerminalIO`
- `TranscriptPrinter`
- MCP 相关测试类（新增）

验收：

- `/mcp` 输出状态清晰
- 状态行可显示 MCP 聚合摘要
- 以下 fixture 跑通或给出明确错误摘要：
  - `fetch` (`sse`)
  - `time` (`stdio`)
  - `bing-cn-mcp-server` (`streamable_http`)

## 3. 建议文件落点

新增文件建议：

- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliMcpConfigManager.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliResolvedMcpConfig.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliResolvedMcpServer.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliMcpRuntimeManager.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliMcpConnectionHandle.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliMcpStatusSnapshot.java`

重点修改文件：

- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliWorkspaceConfig.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/DefaultCodingCliAgentFactory.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
- `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`

## 4. 验证策略

### 单元测试

- 全局 MCP 配置读写
- workspace `enabledMcpServers` 读写
- `type` 归一化规则
- `stdio` 缺失 `command` 的校验
- `sse` / `streamable_http` 缺失 `url` 的校验
- slash completion 对 `/mcp` 系列命令的候选补全

### 集成测试

- `/mcp enable|disable`
- `/mcp pause|resume`
- `/mcp retry`
- MCP 变更后的 session rebinding
- MCP 连接失败但主 CLI 继续可用

### 手工验收

用以下配置进行真实验证：

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

## 5. 风险控制

- 不直接复用 `McpGateway` 全局实例，CLI 使用 session 私有 runtime manager
- 不在一期引入 tool 级控制，避免命令面过度膨胀
- 不在一期引入 OAuth / secret manager，避免把 MCP 和 auth 产品化绑死在一起
- Windows 下 stdio 直接执行命令，不偷偷改写成 `cmd /c`

## 6. 后续衔接

该阶段完成后，下一阶段可以沿同一套 global/workspace/session 模型继续做：

1. Skills
2. Agent / SubAgent / Plan

也就是：

- MCP 先把“外部能力源”接进来
- Skills 再把“可复用任务模板/扩展能力”接进来
- Agent / SubAgent / Plan 最后把“编排层”接到 CLI 交互面
