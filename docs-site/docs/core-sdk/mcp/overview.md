# MCP 总览

`MCP` 在 AI4J 里属于基座能力，但不是 `Tools` 的子目录。

## 1. 它到底解决什么

`MCP` 解决的是：模型如何以协议化方式连接外部能力系统。

所以它讨论的不只是一个 tool schema，而是一整套接入问题：

- transport
- client
- resource
- prompt
- gateway
- server publish

## 2. 为什么它和 `Tools` 平级

因为本地函数工具和协议化外部能力是两条不同的能力线：

- `Tools`：你在本地 JVM 里直接暴露的函数能力
- `MCP`：你通过协议接进来的外部能力面

这也是为什么 `MCP` 不应该被塞进 `Tools` 章节下面。

## 3. 读这一章之前最好先分清什么

最容易讲乱的三件事是：

- 本地函数工具
- `Skill`
- `MCP`

如果这三者没有先分清，后面很容易把 `MCP` 误看成“另一种函数调用”，或者把 `Skill` 误看成“不会执行的工具”。

## 4. 推荐阅读顺序

1. [Positioning and When to Use](/docs/core-sdk/mcp/positioning-and-when-to-use)
2. [Protocol Capabilities](/docs/core-sdk/mcp/protocol-capabilities)
3. [Client Integration](/docs/core-sdk/mcp/client-integration)
4. [Gateway and Multi-service](/docs/core-sdk/mcp/gateway-and-multi-service)
5. [Transport Types](/docs/core-sdk/mcp/transport-types)
6. [Publish Your MCP Server](/docs/core-sdk/mcp/publish-your-mcp-server)
