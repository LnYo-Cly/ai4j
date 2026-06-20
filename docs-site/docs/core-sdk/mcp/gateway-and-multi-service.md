# Gateway and Multi-service

只要接入的 MCP 服务不止一个，真正重要的问题就不再是“单个 client 会不会调”，而是：

- 谁来统一持有这些连接
- 谁来维护工具目录
- 谁来区分全局服务和用户服务
- 谁来处理配置源变更

在 AI4J 里，这个角色就是 `McpGateway`。

## 1. `McpGateway` 的职责比“服务列表”大得多

从实现看，`McpGateway` 至少负责：

1. 管理 `serviceId -> McpClient`
2. 管理工具映射和缓存
3. 从配置文件或配置源加载服务
4. 为请求层提供统一的可用工具查询
5. 为执行层提供统一的工具调用入口
6. 区分全局服务和用户级服务

这意味着 gateway 不是“一个 Map 包装器”，而是 MCP 的运行时控制面。

## 2. 初始化时到底发生什么

`McpGateway.initialize(...)` 当前有两条路径：

### 使用默认配置文件

如果没有显式设置 `configSource`，它会：

1. 加载 `mcp-servers-config.json`
2. 遍历已启用服务
3. 为每个服务创建 `McpClient`
4. 调用 `addMcpClient(...)`
5. 启动完后把自己设成全局实例

### 使用动态配置源

如果先 `setConfigSource(...)`，则会：

1. 把 gateway 绑定到 `McpConfigSource`
2. 初始化时执行 `loadAll(configSource)`
3. 后续继续监听配置增删改事件

所以 gateway 不只是“启动时扫一遍配置”，而是已经具备长期运行的配置治理入口。

## 3. `addMcpClient(...)` 真正做了什么

添加客户端不是简单 put 进 Map。`addMcpClientInternal(...)` 当前会：

1. 把 client 放入 `mcpClients`
2. 立刻执行 `client.connect().join()`
3. 再执行 `toolRegistry.refresh(mcpClients).join()`
4. 如果之前已有旧 client，则断开旧连接
5. 失败时回滚映射并清理新 client

这说明 gateway 在设计上追求的是：

- client 连接成功后才算接入完成
- 工具目录刷新与连接状态联动
- 替换失败时尽量恢复旧状态

## 4. gateway 如何管理工具目录

`McpGatewayToolRegistry` 做了两件核心工作：

- 聚合所有已连接 client 的 `tools/list`
- 构建 `tool -> clientId` 映射

刷新逻辑是：

1. 遍历所有 `mcpClients`
2. 只处理 `client.isConnected()` 的客户端
3. 并发拉取每个 client 的 `getAvailableTools()`
4. 转成 OpenAI `Tool.Function`
5. 覆盖写入新的 cache 和映射

也就是说，gateway 的可见工具目录并不是静态配置，而是“当前连接状态下的聚合快照”。

## 5. 全局服务和用户服务是怎么区分的

AI4J 对多租户不是只停留在概念层。

`McpGatewayKeySupport` 当前明确约定：

- 用户服务 key：`user_{userId}_service_{serviceId}`
- 用户工具 key：`user_{userId}_tool_{toolName}`

这让 gateway 能同时处理：

- 全局共享服务
- 用户专属服务

而且两者不会共用同一套 key 空间。

## 6. `getAvailableTools(...)` 和 `getUserAvailableTools(...)` 的区别

### `getAvailableTools()`

返回全局聚合缓存；如果缓存为空，会触发一次 `toolRegistry.refresh(...)`。

### `getAvailableTools(serviceIds)`

如果传入 `serviceIds`，就只对这些全局服务做过滤并转换。

### `getUserAvailableTools(serviceIds, userId)`

会同时返回：

- 该用户前缀下的专属服务工具
- 过滤后的全局服务工具

这意味着用户级工具不是“覆盖全局工具”，而是与全局工具合并暴露。

## 7. 一个必须明确写出来的现实约束

当前远端全局工具映射规则是：

- 全局工具：`toolName -> clientId`
- 用户工具：`user_{userId}_tool_{toolName} -> clientId`

这带来一个工程后果：

- 用户工具命名空间有隔离
- 全局远端工具命名空间没有服务前缀隔离

也就是说，如果两个全局 MCP 服务都暴露 `search`，后写入的映射会覆盖前者。`McpGatewayToolRegistry` 不会自动改名，也不会为远端服务拼 `serviceName_toolName`。

这是当前多服务设计里最需要在文档中讲清楚的限制之一。

## 8. gateway 和请求白名单是什么关系

`McpGateway` 管的是“系统里已接入哪些服务”，但最终本次请求开放哪些服务，仍然由：

- `ChatCompletion.mcpServices(...)`
- `ResponseRequest.mcpServices(...)`

决定。

两者关系可以记成：

- gateway：目录和连接控制面
- `mcpServices`：请求级暴露控制面

有 gateway 不代表默认开放；有请求白名单也必须先有 gateway 才有服务可选。

## 9. 配置源动态变更时会发生什么

`McpGatewayConfigSourceBinding` 已经把配置源事件和 gateway 操作接起来了：

- `onConfigAdded` -> 创建 client 并接入
- `onConfigUpdated` -> 创建新 client 并替换
- `onConfigRemoved` -> `removeMcpClient(...)`

这说明 AI4J 已经考虑到 MCP 服务不是永远静态的。

如果你把 gateway 只当成“应用启动阶段初始化一次”的组件，会错过这层动态治理能力。

## 10. gateway 不负责什么

它虽然很核心，但它不负责：

- agent loop
- tool approval
- prompt 策略
- provider 响应编排

这些属于更上层运行时。

gateway 的边界是：

- 接入服务
- 管连接
- 管目录
- 管路由

把审批、权限解释或 agent 任务状态直接塞进 gateway，会把层次做乱。

## 11. 调试多服务问题时该先看哪里

建议按这个顺序排查：

1. `McpGateway.getStatus()` 看 client 数量、连接数、工具数
2. `toolRegistry.snapshotMappings()` 看工具映射是否符合预期
3. 检查请求里的 `mcpServices` 是否真的包含目标服务
4. 检查是否出现远端全局同名工具覆盖
5. 再回头看具体 transport 日志

如果一开始就只盯 provider 的 tool call，很容易把问题误判成模型行为异常。

## 12. 这一页的结论

> `McpGateway` 是 AI4J 的 MCP 控制面：它管理多服务连接、配置源、工具目录、用户级隔离和调用路由。它解决的是“服务如何被统一治理”，而不是“本次请求默认开放什么”。同时要注意，当前远端全局工具没有服务名前缀隔离，同名工具在多服务场景下会互相覆盖。
