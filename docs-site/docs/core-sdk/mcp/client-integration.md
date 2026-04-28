# Client Integration

这页讲的是“**我怎么把一个 MCP 服务接进来，并让当前请求真正用上它**”。重点不是协议概念，而是接入路径、生命周期和白名单。

## 1. 从请求入口看，MCP 是怎么挂上的

AI4J 里最关键的不是“有个 MCP client”，而是**当前请求是否显式选择了某个 MCP 服务**。

请求侧的两个关键挂载点是：

- `ChatCompletion.mcpServices`
- `ResponseRequest.mcpServices`

它们的语义不是“直接原样发给 provider”，而是：

1. 当前请求声明本次允许使用哪些 MCP 服务
2. 发送前由本地 runtime 解析这些服务的可见能力
3. 再把解析结果组装进 provider `tools`

所以从第一步开始，AI4J 就把 MCP 设计成了**服务白名单驱动**，不是“网关里有啥就全开啥”。

## 2. 关键源码入口

- 请求对象：`ChatCompletion.java`、`ResponseRequest.java`
- 服务引用对象：`mcp/entity/McpServerReference.java`
- 全局网关：`mcp/gateway/McpGateway.java`
- transport 类型归一化：`mcp/util/McpTypeSupport.java`

这些类连起来，就是 client integration 的完整主线。

## 3. 一个最小接入心智

如果你现在只接 1 个 MCP 服务，最小接入理解方式是：

1. 明确这个服务的 `serviceId`
2. 确认 transport 是 `stdio`、`sse` 还是 `streamable_http`
3. 初始化 `McpGateway`
4. 在请求里显式写 `mcpServices("serviceId")`

从架构角度说，**先选服务，再暴露能力**，而不是反过来。

## 4. `McpServerReference` 有什么用

`McpServerReference` 是请求侧对 MCP 服务的统一引用对象。它不是一个“漂亮的 DTO”，而是把服务连接方式和基础元信息绑定起来。

当前可以直接看出三种常见工厂：

- `McpServerReference.stdio(...)`
- `McpServerReference.http(...)`
- `McpServerReference.sse(...)`

这说明 AI4J 不要求你在业务代码里到处手写 transport 字符串，而是希望你把“服务是什么、怎么连”收敛成一个稳定对象。

## 5. `McpGateway` 在接入时扮演什么角色

对单服务场景来说，你可以把 `McpGateway` 看成：

- MCP client 容器
- 可用能力查询入口
- 服务生命周期的统一宿主

对于 client integration，这有两个直接好处：

- 不需要每次请求都重新连接服务
- 请求侧可以只关心 `serviceId` 白名单，而不用重复处理 transport 和连接细节

## 6. 一次真实请求会发生什么

一条典型链路可以这样理解：

1. 宿主启动或初始化 `McpGateway`
2. gateway 连接好一个或多个 MCP 服务
3. 你在 `ChatCompletion` / `ResponseRequest` 中写 `mcpServices(...)`
4. 发送前 `ToolUtil.getAllTools(..., mcpServices)` 去拿这些服务的可见工具
5. 这些能力被转成 provider 侧 `tools`
6. 模型返回 tool call，再由当前 runtime 决定是否执行、如何执行

所以 client integration 的真正难点不是“连上没有”，而是**让这条链在宿主里保持边界稳定**。

## 7. 哪些问题必须在接入时想清楚

### 7.1 生命周期

- 谁初始化服务
- 谁关闭服务
- 失败后如何重连

### 7.2 可见性

- 当前请求开放哪些服务
- 哪些服务是全局的，哪些是用户级的

### 7.3 权限

- 第三方服务自身有什么真实权限
- 模型是否只应看到其中一小部分能力

### 7.4 错误处理

- 服务挂了是直接失败，还是降级
- 返回错误如何体现在 tool 调用链里

这些问题的优先级都高于“先把 demo 跑起来”。

## 8. 一个常见错误接法

很多人会这样做：

- 先连 5 个服务
- 然后默认全部挂给模型

这几乎一定会带来后续问题：

- 暴露面过大
- 能力边界不清
- 很难追踪某次请求实际使用了哪个服务

AI4J 现在这套 `mcpServices(...)` 设计，本质上就是在阻止这种“全开式接入”。

## 9. 设计摘要

> AI4J 的 MCP client integration 不是把远端服务直接塞进 provider，而是先把服务接到 `McpGateway`，再由请求侧通过 `mcpServices` 做显式白名单，最后把可见能力转换成 provider `tools`。这样 transport、生命周期和能力暴露面是分层治理的。

## 10. 继续阅读

- [MCP / Gateway and Multi-service](/docs/core-sdk/mcp/gateway-and-multi-service)
- [MCP / Tool Exposure Semantics](/docs/core-sdk/mcp/tool-exposure-semantics)
