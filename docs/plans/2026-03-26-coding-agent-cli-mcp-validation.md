# 2026-03-26 Coding Agent CLI MCP 真实性验证

- 状态：Done
- 范围：`ai4j-cli` MCP consumer runtime / `/mcp` 命令面 / 实际 MCP 连通性 / 真实模型端到端调用

## 1. 验证前提

- 构建产物：`ai4j-cli/target/ai4j-cli-2.0.0-jar-with-dependencies.jar`
- 本机环境：
  - `uvx 0.7.9`
  - 可访问外网
  - 已存在 `ZHIPU_API_KEY`

## 2. 使用的真实 MCP 配置

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

workspace 配置：

```json
{
  "enabledMcpServers": ["fetch", "time", "bing-cn-mcp-server"]
}
```

## 3. 真实性验证结果

### 3.1 直接初始化 MCP runtime

结果：

- `fetch`：`connected`
- `time`：`connected`
- `bing-cn-mcp-server`：`connected`

实际拿到的工具：

- `time`
  - `get_current_time`
  - `convert_time`
- `bing-cn-mcp-server`
  - `bing_search`
  - `crawl_webpage`

结论：

- `stdio` transport：已真实连通
- `streamable_http` transport：已真实连通
- `sse` transport：已真实连通，`fetch` 可完成 `initialize` 和 `tools/list`

### 3.2 直接调用真实 MCP 工具

调用结果：

- `get_current_time({"timezone":"Asia/Shanghai"})`
  - 成功返回 `2026-03-26T17:31:04+08:00`
- `bing_search({"query":"OpenAI","count":3})`
  - 成功返回搜索结果 JSON

结论：

- 不只是“连上了”
- `ToolExecutor` 已可对真实 MCP 服务完成实际调用

### 3.3 真实模型端到端验证

验证方式：

- provider：`zhipu`
- protocol：`chat`
- model：`glm-4.7`
- base-url：`https://open.bigmodel.cn/api/coding/paas/v4`
- 启用 `time` MCP
- 提示词强制要求调用 `get_current_time`

结果：

- 模型实际发起了 `get_current_time`
- MCP 返回时间结果
- 最终 assistant 正常输出：

```text
2026-03-26T17:31:49+08:00 Thursday
```

结论：

- `ai4j-cli` 已完成真实模型 -> MCP tool -> tool result -> assistant final answer 的完整闭环

## 4. 当前判定

- `/mcp` 命令面：通过
- MCP runtime rebinding：通过
- `stdio`：通过
- `streamable_http`：通过
- `sse`：通过
- 真实模型工具调用闭环：通过
- `fetch` 对应的当前 SSE endpoint：通过

## 5. 当前剩余问题

1. `SseTransport` 的显式 `disconnect()` 仍会先触发一次既有的重连日志，再由 shutdown 停止；这不是本次修复范围，但行为仍可继续收敛。
2. `/mcp edit` 还未实现

## 6. 新增验证结论

- 问题已定位为 `ai4j` SDK 里的 legacy `sse` transport 兼容性问题，不是 CLI 命令面问题。
- 修复点位于 `ai4j/src/main/java/io/github/lnyocly/ai4j/mcp/transport/SseTransport.java`。
- 兼容策略：
  - 使用 `HttpURLConnection` 建立 `GET text/event-stream` 连接并手动解析 SSE 帧；
  - 使用 `HttpURLConnection` 发送 JSON-RPC `POST` 消息；
  - 正确处理 `event` / `data` / `id`、默认 `message` 事件、以及多行 `data`。
- 真实 endpoint 验证结果：
  - `CONNECTED=true`
  - `INITIALIZED=true`
  - `TOOLS=1`
  - `TOOL=fetch`
  - `DISCONNECTED_OK`
