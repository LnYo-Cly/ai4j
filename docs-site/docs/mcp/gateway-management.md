---
sidebar_position: 5
---

# MCP Gateway 管理（多服务聚合与治理）

`McpGateway` 不是“多建几个 `McpClient` 的包装类”，而是 AI4J 里真正的多服务 MCP 运行时。

它同时负责 4 件事：

- 保存所有已接入的 `McpClient`
- 维护 `tool -> client` 的目录映射
- 统一管理全局服务和用户级服务
- 把配置源、动态增删和上层 Agent 调用收敛到一个入口

如果你只接一个 MCP，`McpClient` 就够了；如果你要做服务治理、租户隔离、动态启停，核心入口就是 `mcp/gateway/McpGateway.java`。

## 1. 先看它内部到底管理什么

`McpGateway` 内部有 3 个关键状态：

- `mcpClients`
  保存 `clientKey -> McpClient`
- `toolRegistry`
  由 `McpGatewayToolRegistry` 维护 `toolKey -> clientKey`
- `configSource`
  绑定 `McpConfigSource`，负责把配置系统变更翻译成网关增删改

这意味着 gateway 管理的不是“工具列表快照”，而是：

- 连接状态
- 工具目录
- 配置源绑定关系
- 生命周期入口

## 2. key 规则就是它的多租户边界

AI4J 没把用户隔离做成注释约定，而是直接写进 key 规则里。

### 全局服务

- client key: `serviceId`
- tool key: `toolName`

### 用户级服务

- client key: `user_{userId}_service_{serviceId}`
- tool key: `user_{userId}_tool_{toolName}`

对应实现入口：

- `McpGatewayKeySupport.buildUserClientKey(...)`
- `McpGatewayKeySupport.buildUserToolKey(...)`
- `McpGatewayKeySupport.extractUserIdFromClientKey(...)`

这带来两个直接结果：

- `callUserTool(...)` 可以先查用户专属工具，再回退到全局工具
- `getUserAvailableTools(...)` 可以把用户工具和全局工具在一个结果里合并

## 3. 初始化不是“读个 JSON”这么简单

### 模式 A：配置文件初始化

```java
McpGateway gateway = new McpGateway();
gateway.initialize("mcp-servers-config.json").join();
```

当 `configSource == null` 时，`initialize(...)` 的执行链是：

1. `loadServerConfig(configFile)`
2. `startConfiguredServers()`
3. 对每个启用服务调用 `clientFactory.create(...)`
4. `addMcpClient(...)`
5. `client.connect().join()`
6. `toolRegistry.refresh(mcpClients).join()`

这里有一个很重要的真实语义：

- 配置文件里只有 `enabled == true` 的服务会被启动
- 单个服务启动失败会被记录日志并吞掉
- 整个 gateway 仍然可能被标记为 `initialized = true`

所以 `initialize()` 成功，不等于“所有服务都连上了”，它只表示网关初始化流程完成了。

### 模式 B：配置源初始化

```java
McpConfigSource source = new FileMcpConfigSource("mcp-servers-config.json");
McpGateway gateway = new McpGateway();
gateway.setConfigSource(source);
gateway.initialize().join();
```

当你设置了 `configSource` 后，`initialize()` 不再走本地配置文件解析，而是走：

1. `configSourceBinding.loadAll(configSource)`
2. 从配置源取回全部配置
3. 为每个配置创建 client 并接入 gateway

这条路径更适合：

- 数据库配置中心
- 后台管理台
- 多租户动态接入平台

## 4. 动态增删是“重建目录”，不是局部打补丁

`addMcpClientInternal(...)` 的真实顺序是：

1. 先把新 client 放进 `mcpClients`
2. `client.connect().join()`
3. `toolRegistry.refresh(mcpClients).join()`
4. 成功后再断开旧 client
5. 如果失败，回滚 `mcpClients`，再断开新 client

`removeMcpClientInternal(...)` 的顺序是：

1. 从 `mcpClients` 删除
2. `client.disconnect().join()`
3. `toolRegistry.refresh(mcpClients).join()`

这说明当前实现的目录更新策略是：

- 每次增删改后，重新向所有已连接 client 拉一遍工具清单
- 然后整体重建 `tool -> client` 映射缓存

优点是实现简单、状态一致性强。缺点是：

- 服务很多时，刷新成本会线性放大
- 不是增量更新

## 5. `McpGatewayToolRegistry` 真正做了什么

`McpGatewayToolRegistry.refresh(...)` 会：

1. 遍历所有 `mcpClients`
2. 对每个已连接 client 调 `getAvailableTools()`
3. 把 `McpToolDefinition` 转成 OpenAI 风格 `Tool.Function`
4. 同步生成 `toolKey -> clientKey` 映射
5. 刷新 `availableTools` 缓存

这里还有一个很容易被忽略但非常关键的事实：

- 如果两个全局服务暴露了同名工具，映射会互相覆盖
- 当前实现没有为全局同名工具做命名空间隔离
- 最终命中哪个 client，取决于 refresh 时最后写入的那一个

所以在多服务平台里，`toolName` 命名规范不是建议，而是硬要求。

## 6. 调用路径怎么走

### 全局工具

```java
String result = gateway.callTool("query_weather", arguments).join();
```

调用链是：

1. `toolRegistry.getClientId(toolName)`
2. 找到对应 `clientKey`
3. `mcpClients.get(clientKey)`
4. `client.callTool(toolName, arguments)`

如果找不到映射，会直接抛：

- `IllegalArgumentException("工具不存在: ...")`

如果 client 不存在或未连接，会抛：

- `IllegalStateException("MCP客户端不可用: ...")`

### 用户工具

```java
String result = gateway.callUserTool("u1001", "query_weather", arguments).join();
```

调用链是：

1. 构造 `user_{userId}_tool_{toolName}`
2. 先查用户级映射
3. 命中则走对应用户 client
4. 未命中则回退到全局 `callTool(...)`

这个回退策略很实用，但也要意识到它的含义：

- 用户级未命中，不代表该工具不可用
- 可能只是落回了全局共享服务

如果你的业务要求“租户强隔离，不允许回退”，就不能直接照搬默认策略。

## 7. `getAvailableTools(...)` 和 `getUserAvailableTools(...)` 的边界

### `getAvailableTools()`

- 返回所有全局工具缓存
- 如果缓存为空，会先 refresh 一次

### `getAvailableTools(serviceIds)`

- 只过滤全局 `clientKey`
- 本质上是按 `serviceId` 取指定 client 的工具集合

### `getUserAvailableTools(serviceIds, userId)`

- 先枚举用户级 client
- 再合并全局 client
- `serviceIds` 只对全局 client 过滤生效

所以它不是“只返回用户级工具”，而是“用户级工具 + 可见的全局工具”。

## 8. 配置源热更新到底怎么生效

`McpGatewayConfigSourceBinding` 是配置源和网关之间的桥。

它会把 `McpConfigSource` 的 3 类事件翻译成实际动作：

- `onConfigAdded` -> 创建 client -> `gateway.addMcpClient(...)`
- `onConfigUpdated` -> 重新创建 client -> `gateway.addMcpClient(...)`
- `onConfigRemoved` -> `gateway.removeMcpClient(...)`

这意味着 AI4J 现在已经具备了“配置变更驱动运行时服务切换”的基础骨架。

但也要注意边界：

- `FileMcpConfigSource` 只提供 `reloadConfigs()`，不会自动监控文件系统
- MySQL / Redis / 配置中心要靠你自己实现 `McpConfigSource`

## 9. `getGatewayStatus()` 能看什么，不能看什么

`getGatewayStatus()` 当前能提供：

- `totalClients`
- `globalClients`
- `userClients`
- `connectedClients`
- `totalTools`
- 每个 client 的 `connected / initialized / type`

它适合：

- 管理台状态页
- 启动后自检
- 回归测试断言

但它还不是完整观测面，因为里面没有：

- 每个 tool 的调用量
- 延迟分布
- 失败率
- 最近一次异常原因

这类指标仍然需要你在网关外围补监控和日志。

## 10. 与 Agent 集成时真正的分层关系

推荐结构是：

1. 宿主启动时初始化 `McpGateway`
2. Agent 构建时显式声明 `mcpServices`
3. 运行时由 `ToolUtil` 通过 gateway 完成远端调用

```java
Agent agent = Agents.react()
        .toolRegistry(Collections.<String>emptyList(), Arrays.asList("github", "filesystem"))
        .build();
```

要点只有一句：

- gateway 解决“服务怎么管理”
- `toolRegistry(..., mcpServices)` 解决“这次给模型看哪些服务”

两者不是一回事。

## 11. 什么时候该用它，什么时候不该用它

适合上 `McpGateway` 的场景：

- 服务数量超过一个
- 需要统一管理多种 transport
- 需要用户级隔离
- 需要动态启停和配置源治理

不必一开始就上 gateway 的场景：

- 只接一个 MCP
- 只是验证 transport 是否能连通
- 还没跑通最基本的 `connect -> tools/list -> tools/call`

## 12. 这页最该记住的结论

`McpGateway` 在 AI4J 里不是“辅助类”，而是 MCP 多服务治理层本身。

它把：

- 连接生命周期
- 工具目录
- 用户隔离
- 配置源热更新

收敛成一个正式运行时；但它不负责解决所有问题，例如命名冲突治理、失败熔断、审计指标，仍然需要在平台层继续补齐。
