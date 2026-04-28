# MCP 总览

`MCP` 在 AI4J 里属于基座能力，但不是 `Tools` 的子目录。

## 1. 先确定它在整套体系里的位置

这里讲的是协议化外部能力接入。

它先属于 `Core SDK`，然后才可能被上层：

- `Agent`
- `Coding Agent`
- `Flowgram`

复用成更具体的运行时能力。

## 2. 它到底解决什么

`MCP` 解决的是：模型如何以协议化方式连接外部能力系统。

所以它讨论的不只是一个 tool schema，而是一整套接入问题：

- transport
- client
- resource
- prompt
- gateway
- server publish

## 3. 为什么它和 `Tools` 平级

因为本地函数工具和协议化外部能力是两条不同的能力线：

- `Tools`：你在本地 JVM 里直接暴露的函数能力
- `MCP`：你通过协议接进来的外部能力面

这也是为什么 `MCP` 不应该被塞进 `Tools` 章节下面。

## 4. 什么时候应该优先想到 MCP

更适合 MCP 的情况通常是：

- 能力不在本地 JVM 内部
- 你希望按协议统一接外部工具面
- 未来可能要接多服务、多 transport、多能力源
- 你希望 `tool / resource / prompt` 不只是零散临时代码

如果只是本地 Java 函数，优先看 `Tools`。

## 5. 读这一章之前最好先分清什么

最容易讲乱的三件事是：

- 本地函数工具
- `Skill`
- `MCP`

如果这三者没有先分清，后面很容易把 `MCP` 误看成“另一种函数调用”，或者把 `Skill` 误看成“不会执行的工具”。

## 6. 推荐阅读顺序

1. [Positioning and When to Use](/docs/core-sdk/mcp/positioning-and-when-to-use)
2. [Protocol Capabilities](/docs/core-sdk/mcp/protocol-capabilities)
3. [Client Integration](/docs/core-sdk/mcp/client-integration)
4. [Gateway and Multi-service](/docs/core-sdk/mcp/gateway-and-multi-service)
5. [Transport Types](/docs/core-sdk/mcp/transport-types)
6. [Publish Your MCP Server](/docs/core-sdk/mcp/publish-your-mcp-server)

如果你要讲清楚 AI4J 为什么把 `MCP` 单独成章，这一页和 `Tools` / `Skills` 的边界就是最核心的答案。

## 7. 关键对象

如果你准备从源码验证这一章的判断，优先看下面几个入口：

- `config/McpConfig.java`：服务接入配置入口
- `mcp/gateway/McpGateway.java`：多服务连接、目录和生命周期管理中心
- `mcp/util/McpTypeSupport.java`：transport 类型归一化入口
- `ChatCompletion.mcpServices` 与 `ResponseRequest.mcpServices`：请求侧服务白名单挂载点

从这些对象已经可以看出，MCP 在 AI4J 里是完整的协议接入面，而不是一个零散的工具列表。

## 8. 阅读这一章时要抓住什么

- capability 与 tool projection 不是一回事，MCP tool 只是协议能力在请求链里的运行时投影
- gateway 解决的是统一管理，请求白名单解决的是本次可见性，两者不能混成一个概念
- transport 不是 capability 本身，但它决定接入方式、部署模型和连接治理

把这三条先建立起来，再去看每一篇子页面时，就不会把 MCP 误解成“远程函数调用的另一种写法”。
