---
sidebar_position: 2
---

# MCP 使用路径与场景选择

第一次看 MCP 文档时，最容易把三种完全不同的问题混成一个：

- 我想连别人的 MCP
- 我想同时管很多 MCP
- 我想把自己的能力发布成 MCP

这页的目标就是把这三条线拆开，并告诉你每条线真正该先看什么。

## 1. 先按“你要解决的问题”选路线

不要按目录顺序读，先按问题类型分流。

### 路线 A: 你只想接一个现成 MCP

这是最常见起点。

典型目标：

- 连上一个远端或本地 MCP server
- 列出它的工具
- 调一次 `tools/call`
- 再判断是否要接到 Agent 或 Coding Agent

这一条路径的关键对象是：

- `McpTransport`
- `McpClient`
- `TransportConfig`

推荐阅读：

1. [MCP 传输类型详解](/docs/mcp/transport-types)
2. [MCP Client 接入（单服务模式）](/docs/mcp/client-integration)
3. [接入第三方 MCP（全部方式）](/docs/mcp/third-party-mcp-integration)

### 路线 B: 你已经不止一个 MCP，要做治理

当你开始同时接：

- GitHub
- 浏览器
- 数据库
- 内部 API

并且还要考虑：

- 用户隔离
- 动态启停
- 配置重载
- 工具来源治理

就不应该继续停留在“多建几个 `McpClient`”的思路里。

这一条路径的关键对象是：

- `McpGateway`
- `McpGatewayToolRegistry`
- `McpConfigSource`

推荐阅读：

1. [MCP Gateway 管理](/docs/mcp/gateway-management)
2. [Tool 暴露语义与安全边界](/docs/mcp/tool-exposure-semantics)
3. [接入第三方 MCP（全部方式）](/docs/mcp/third-party-mcp-integration)

### 路线 C: 你要把自己的 Java 能力发布成 MCP

这和“接别人家的 MCP”是相反方向的问题。

典型目标：

- 把内部服务方法发布成 Tool
- 把业务数据发布成 Resource
- 把模板发布成 Prompt
- 决定用 stdio、SSE 还是 streamable HTTP 提供出去

这一条路径的关键对象是：

- `@McpService`
- `@McpTool`
- `@McpResource`
- `@McpPrompt`
- `McpServerFactory`

推荐阅读：

1. [构建并对外发布 MCP Server](/docs/mcp/build-your-mcp-server)
2. [MCP 传输类型详解](/docs/mcp/transport-types)
3. [Tool 暴露语义与安全边界](/docs/mcp/tool-exposure-semantics)

## 2. 不同路线对应的系统边界完全不同

这是最重要的判断点。

### 单服务 client 路线

你主要关心：

- transport 能不能连上
- initialize 是否完成
- tool/resource/prompt 能不能正确拿到

### gateway 路线

你主要关心：

- 哪个工具属于哪个 client
- 用户级和全局级怎么隔离
- 配置变更后怎样动态生效

### server 路线

你主要关心：

- 哪些本地能力可以对外发布
- 发布出的 contract 长什么样
- 本地注解和对外 MCP 能力如何映射

如果这三类问题不先拆开，文档和实现都会混层。

## 3. 什么时候不该急着上 Gateway

只要你当前还处于下面阶段，就不要先上 gateway：

- 你只接一个 MCP
- 你还没跑通一次 `connect -> getAvailableTools -> callTool`
- 你还不清楚 transport 差异

原因很简单：

- 单服务问题先解决 transport 和初始化
- 多服务问题再解决治理和映射

过早上 gateway，只会把连接问题和治理问题叠在一起。

## 4. 什么时候应该立刻上 Gateway

只要出现下面任一情况，就不该继续停留在单 client 思路：

- 服务数量超过一个
- 需要按用户隔离工具
- 需要动态加服务 / 删服务
- 需要统一观察工具来源

因为从源码看，`McpGateway` 已经不是“client list”，而是：

- 独立 tool registry
- key 规则
- 配置源绑定
- 多 client 调度入口

## 5. 什么时候该考虑 Tool 暴露语义，而不是只看连通性

很多人连通一个 MCP 后就直接接 Agent，这是不稳的。

在 AI4J 里，更应该先确认：

- `getAllTools(functionList, mcpServerIds)` 是否只暴露了你明确要的工具
- 是否误把 `getLocalMcpTools()` 用在普通 Agent 消费场景
- 本地 MCP 工具、Function 工具和远程 gateway 工具有没有命名冲突

也就是说，连通性之后，第二个问题不是“能不能调用”，而是“该不该暴露给模型”。

## 6. MCP 和 Agent / Coding Agent 的关系

它们经常一起出现，但不是同一层。

更准确地说：

- `MCP` 负责能力连接和发布
- `Agent` 负责推理时怎样消费这些能力
- `Coding Agent` 负责宿主环境怎样加载和治理这些能力

因此：

- 先把 MCP 自身跑通
- 再接 Agent / Coding Agent

是更稳的路线。

## 7. 一张最简判断表

| 你的问题 | 优先走哪条线 |
| --- | --- |
| 我只想连一个现成 MCP | 单服务 Client |
| 我要同时管理多个 MCP | Gateway |
| 我要把 Java 能力发布给别人 | MCP Server |
| 我担心工具暴露过宽 | Tool Exposure |

## 8. 推荐起步顺序

如果你现在完全不确定怎么开始，推荐顺序是：

1. 先用单服务模式接一个 MCP
2. 再理解 transport 选型
3. 再进入 tool exposure 语义
4. 最后再做 gateway 或 server 发布

这个顺序的好处是：你会先解决“能连”，再解决“能管”，最后解决“能发布”。
