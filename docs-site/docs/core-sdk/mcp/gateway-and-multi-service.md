# Gateway and Multi-service

一旦你接的不止一个 MCP 服务，就不该继续停留在“单 client 心智”。这时真正重要的不是每个服务各自会不会调，而是**谁来统一管理这些服务**。在 AI4J 里，这个角色就是 `McpGateway`。

## 1. `McpGateway` 到底是什么

从源码看，`mcp/gateway/McpGateway.java` 至少承担了四个职责：

1. 管理多个 `McpClient`
2. 维护工具名到客户端的映射关系
3. 负责初始化、加载配置、启动服务
4. 向上暴露统一的“可用工具”和调用入口

所以 gateway 不是“工具列表集合”，而是 **MCP 运行时调度中心**。

## 2. 为什么多服务必须有网关

单服务时，你可能还能靠一些局部对象把事情串起来；但只要进入多服务，立刻会出现这些问题：

- 当前请求到底该看见哪些服务
- 服务断连之后由谁负责重连
- 同名或相似工具如何组织
- 多租户或多用户场景怎么隔离

这些问题如果散落在业务代码里手写分支，很快就会失控。

`McpGateway` 的价值就在于：**把这些横切问题统一收进一个正式 runtime 组件。**

## 3. AI4J 里的关键设计点

### 3.1 全局实例

源码里有：

- `getInstance()`
- `setGlobalInstance(...)`
- `clearGlobalInstance()`

这说明 AI4J 已经把 gateway 视为宿主级共享组件，而不是每次请求临时 new 的对象。

### 3.2 client registry

`McpGateway` 内部维护的是：

- client key -> `McpClient`
- tool name -> client 映射

这意味着它既知道“有哪些服务”，也知道“某个能力属于哪个服务”。

### 3.3 config source

它不仅能从默认配置文件 `mcp-servers-config.json` 初始化，还支持：

- `McpConfigSource`
- `FileMcpConfigSource`
- 动态 rebind / loadAll

这说明 AI4J 并不是只面向静态 demo 配置，而是给正式配置源治理留了入口。

## 4. 全局服务与用户级服务怎么区分

这部分是很多文档最容易写漏，但工程上非常关键的一点。

在源码里，client key 已经区分了：

- 全局服务：`serviceId`
- 用户级服务：`user_{userId}_service_{serviceId}`

工具侧也有对应的用户级 key 语义。

这说明 AI4J 对多服务的理解不是“多连几个第三方”，而是已经把 **用户级能力隔离** 作为正式问题来对待。

## 5. Gateway 和请求白名单是什么关系

很多人会误以为有了 gateway，就说明所有已连接服务都默认对模型可见。

这不对。

更准确的关系是：

- `McpGateway` 负责管理服务和能力目录
- `mcpServices(...)` 负责决定本次请求开放哪些服务

也就是说：

- gateway 负责“有哪些能力”
- 请求白名单负责“这次给模型看哪些能力”

两者缺一不可。

## 6. 什么时候你应该从“单服务接法”升级到 gateway 思维

出现这些情况时，就不该再把 MCP 当成局部接入：

- 同时接 2 个以上 MCP 服务
- 服务需要按租户、用户、工作区动态启停
- 你需要统一处理配置变更、重连、关闭
- 你需要追踪某个 tool 到底来自哪个服务

只要命中一条，就应该把 `McpGateway` 当成正式基础设施。

## 7. 它和 Agent / Coding Agent 的关系

`McpGateway` 仍然属于 Core SDK 基座层，它不负责：

- agent loop
- 人机审批
- 长任务 checkpoint

但它会成为上层 runtime 的重要依赖，因为所有外部协议能力最终都要先有一个稳定的网关宿主。

所以你可以把它理解成：

- Core SDK 里的外部能力底座
- 上层 Agent runtime 的协议输入面

## 8. 设计摘要

> AI4J 用 `McpGateway` 统一管理多服务 MCP 连接、配置源、工具目录和用户级隔离；而具体某次请求是否能看到某个服务，还要再经过 `mcpServices` 白名单控制。所以 gateway 解决的是“管理”，不是“默认全开”。

## 9. 继续阅读

- [MCP / Client Integration](/docs/core-sdk/mcp/client-integration)
- [MCP / Tool Exposure Semantics](/docs/core-sdk/mcp/tool-exposure-semantics)
