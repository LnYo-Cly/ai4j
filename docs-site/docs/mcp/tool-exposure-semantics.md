---
sidebar_position: 5
---

# Tool 暴露语义与安全边界

这一页讲的是 MCP 在 AI4J 里最容易被误解的地方：

> “工具能被调用” 和 “工具应该被暴露给模型” 不是一回事。

如果不先把这条边界讲清楚，MCP 接入很容易从“能力增强”变成“暴露面失控”。

## 1. 先把 3 种工具来源分开

在 AI4J 当前实现里，和 MCP 相关的工具来源至少有三种：

### 1.1 本地 MCP 工具

来源于：

- `@McpService`
- `@McpTool`

由 `ToolUtil.scanMcpTools()` 反射扫描并注册到本地缓存。

### 1.2 远程 / 网关 MCP 工具

来源于：

- `McpGateway`
- 已连接的 `McpClient`

### 1.3 传统 Function 工具

来源于：

- `@FunctionCall`

这说明 AI4J 的工具平面不是只有 MCP，而是 MCP 和传统 Function 工具并存。

## 2. `getAllTools(...)` 的真实语义是什么

当前最重要的入口是：

- `ToolUtil.getAllTools(functionList, mcpServerIds)`

它的真实语义非常明确：

- 只合并你显式传入的 function 列表
- 只合并你显式传入的 MCP server 列表

也就是说，它不是“把系统里所有可用工具都自动开放出来”。

这是一个非常重要的安全设计决策。

## 3. `getLocalMcpTools()` 不是同一套语义

另一个容易混淆的入口是：

- `ToolUtil.getLocalMcpTools()`

它会把本地扫描到的 `@McpService / @McpTool` 能力全部枚举出来。

更适合：

- 本地 MCP 能力发布
- 本地能力枚举
- MCP server 侧暴露能力整理

不适合直接等同于：

- 普通 Agent 当前这次请求要开放什么

所以：

- `getAllTools(...)` 更像调用点白名单
- `getLocalMcpTools()` 更像本地能力枚举面

## 4. 为什么“显式传入”比“默认全开”更合理

如果默认全量注入，至少会带来 3 类问题。

### 4.1 权限面会静悄悄变大

调用方以为自己只开放了几个工具，模型实际上却拿到了更多能力。

### 4.2 调试会变得很模糊

一旦工具冲突或误调用，很难立刻判断：

- 是本地 MCP 工具
- 是远程 gateway 工具
- 还是传统 Function 工具

### 4.3 审批和审计无法稳定收口

只有调用点显式声明暴露面，后续白名单、审批、trace、回归审计才有稳定基础。

## 5. 调用优先级和暴露面是两套逻辑

这一点很关键，很多文档会把两者混写。

从 `ToolUtil.invoke(...)` 的真实逻辑看，调用优先级大致是：

1. built-in tool
2. 用户级 MCP 工具
3. 本地 MCP 工具
4. 传统 Function 工具
5. 全局 MCP gateway 工具

这代表：

- 工具实际被谁执行，取决于调用时优先级
- 工具是否应该暴露给模型，取决于 `getAllTools(...)` 的白名单选择

这是两套不同问题。

## 6. 本地 MCP 工具是怎样被扫描出来的

`ToolUtil.scanMcpTools()` 会：

1. 反射扫描带 `@McpService` 的类
2. 找到其中带 `@McpTool` 的方法
3. 生成 API 友好的 tool id
4. 放进本地 `mcpToolCache`

因此本地 MCP 工具并不是手工注册在某个固定列表里，而是通过注解扫描进入缓存。

### 这带来的含义

如果你把一个高风险能力标成 `@McpTool`，它很可能在本地能力枚举面里天然可见。

这也是为什么“是否暴露给模型”必须继续由上层白名单控制。

## 7. Gateway 工具暴露为什么还要引入 serviceId

远程 MCP 工具不是直接平铺的。

`getAllTools(functionList, mcpServerIds)` 之所以要求传 `mcpServerIds`，原因是：

- 它要明确你这一轮只允许从哪些 MCP 服务取工具

这让工具暴露面从“按全局默认”变成了“按服务白名单”。

### 在用户级场景下更明显

当前 gateway key 规则里有：

- `user_{userId}_service_{serviceId}`
- `user_{userId}_tool_{toolName}`

这说明用户隔离也不是临时判断，而是进了 key 和映射规则。

## 8. 什么时候应该用哪种暴露方式

### 普通 Agent / Chat 场景

优先：

- `getAllTools(functionList, mcpServerIds)`

因为你要的是当前请求级白名单。

### MCP Server 发布场景

更常会关心：

- `getLocalMcpTools()`

因为你要整理本地可发布能力，而不是当前对话该开放什么。

### Gateway 治理场景

更该关心：

- serviceId 选择
- 用户级和全局级映射
- 工具冲突与来源治理

## 9. 代码审查时真正该检查什么

这页最实用的部分其实是 review checklist。

### 9.1 暴露面是否显式

检查：

- 是否调用点明确传了 `functionList`
- 是否明确传了 `mcpServerIds`

### 9.2 是否误用本地枚举入口

检查：

- 有没有把 `getLocalMcpTools()` 直接拿去给普通 Agent 当默认工具集

### 9.3 是否存在命名冲突

检查：

- 本地 MCP tool id
- Function 工具名
- 远程 gateway 工具名

是否可能撞名，导致调用优先级出乎意料。

### 9.4 高风险工具是否做二次控制

检查：

- 文件系统写操作
- 浏览器自动操作
- 外部服务 mutation 类调用

是否有额外审批或白名单。

## 10. 最后的边界判断

如果只记一句话：

- `getAllTools(...)` 解决“这次要开放什么”
- `getLocalMcpTools()` 解决“本地 MCP 能力有哪些”
- `ToolUtil.invoke(...)` 解决“最终由谁执行”

把这三层分开，MCP 工具治理才不会混乱。
